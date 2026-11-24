// sym_rot30.java: from cube_re0300.sim (steady Re 300), clear the solution and rebuild the fluid
// with chained part operations, which STAR-CCM+ re-executes on demand (rotating the parts directly left the
// pipeline up to date: 0.00 s, 501860 cells, cube not rotated):
//   rot30_transform  rotation 30 deg about lab x, inputs cube_solid and refine_wake (in place)
//   rot30_subtract   domain_box minus the transformed cube_solid only; output renamed fluid_rot30
//   mesh_rot30       copy of mesh_cube (same settings), input fluid_rot30; refine_wake stays a
//                    volume control only
// Region fluid gets fluid_rot30 in place of the part fluid; each boundary keeps its name, conditions
// and reports, its part surfaces swapped by name. The three operations are executed, the cell count is
// read and the macro throws if it is still the base 501860. Then the transient set-up of the aligned case,
// inlet velocity and uniform initial velocity U*d (U read from the inlet, d from INFLOW_DEG and
// INFLOW_PHI_DEG; [1.0, 0.0, 0.0] m/s at INFLOW_DEG = 0, inlet untouched), no perturbation, 14500 steps
// (725 t*U/D), one CSV row per step. mesh_cube and the part fluid stay in the file, unused.
// Run on a copy: starccm+ -batch sym_rot30.java cube_re0300.sim. The loaded .sim is never saved
// over; outputs go next to it (session dir). Assumes D = 1 m, U = 1 m/s, so 1 s = 1 D/U, and the cube
// centred at the origin (the rotation keeps the default origin of the recording).
// TILT_DEG != 0 adds rot30_tilt after rot30_transform, on cube_solid only: the body is tilted with the
// flow kept axial (the lateral symmetry planes cancel any transverse inflow component).
package macro;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import java.util.*;
import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;
import star.meshing.*;

public class sym_rot30 extends StarMacro {

  private static final double DT = 0.05;
  private static final double T_END = 725.0;
  private static final int STEPS = 14500;
  private static final String[] MONITORS = {"cd_monitor", "cl_monitor", "cy Monitor"};
  private static final double ROT_DEG = 30.0;
  // Lateral half-width factor: y and z of domain_box's corners are multiplied by DOMAIN_SCALE before its
  // faces are split (x, cube_solid and refine_wake untouched). 1.0 leaves the domain as in the .sim.
  private static final double DOMAIN_SCALE = 1.0;
  // Body tilt b = TILT_DEG, phi = TILT_PHI_DEG in the y-z plane from +y towards +z (same convention as
  // INFLOW_*). TILT_DEG = 0 adds no operation and leaves the macro as before.
  private static final double TILT_DEG = 0.0;
  private static final double TILT_PHI_DEG = 45.0;
  // Cube centre after rot30_transform: the cube is centred at the origin and rotated about lab x
  // through the origin, so it stays there (header assumption).
  private static final double[] CUBE_CENTRE = {0.0, 0.0, 0.0};
  // Inflow direction d = (cos b, sin b cos phi, sin b sin phi): b = INFLOW_DEG from +x, phi =
  // INFLOW_PHI_DEG in the y-z plane from +y towards +z. INFLOW_DEG = 0 leaves the inlet as in the .sim.
  private static final double INFLOW_DEG = 0.0;
  private static final double INFLOW_PHI_DEG = 45.0;
  private static final String INLET = "inlet";
  private static final long BASE_CELLS = 501860L;

  private static final String CUBE_PART = "cube_solid";
  private static final String DOMAIN_PART = "domain_box";
  private static final String WAKE_PART = "refine_wake";
  private static final String OLD_FLUID_PART = "fluid";
  private static final String REGION = "fluid";
  private static final String BASE_MESH = "mesh_cube";
  private static final String[] BASE_CONTROLS = {"cube_refinement", "wake_refinement"};
  // The Subtract output part takes the name of the Subtract operation itself (SUBTRACT_OP);
  // renamed at once to FLUID_PART.
  private static final String SUBTRACT_OUTPUT = "rot30_subtract";
  private static final String FLUID_PART = "fluid_rot30";
  private static final String TRANSFORM_OP = "rot30_transform";
  private static final String TILT_OP = "rot30_tilt";
  private static final String SUBTRACT_OP = "rot30_subtract";
  private static final String MESH_OP = "mesh_rot30";
  private static final double FACE_SPLIT_ANGLE = 89.0;
  // Fixed direction order -x,+x,+y,-y,+z,-z. splitAndNameBoxFaces no longer trusts the split's output
  // order (confirmed unreliable); each name below is matched to its face by geometry instead.
  private static final String[] CUBE_FACE_NAMES =
    {"cube_front", "cube_back", "cube_left", "cube_right", "cube_top", "cube_bottom"};
  private static final String[] DOMAIN_FACE_NAMES =
    {"inlet", "outlet", "lateral_yplus", "lateral_yminus", "lateral_zplus", "lateral_zminus"};

  private Simulation sim;

  @Override
  public void execute() {
    sim = getActiveSimulation();
    sim.clearSolution();
    sim.println("sym_rot30: solution cleared");
    checkFreeNames();

    SimulationPartManager parts = sim.get(SimulationPartManager.class);
    GeometryPart cube = part(CUBE_PART);
    GeometryPart domain = part(DOMAIN_PART);
    GeometryPart wake = part(WAKE_PART);
    GeometryPart oldFluid = part(OLD_FLUID_PART);
    MeshOperationManager ops = sim.get(MeshOperationManager.class);

    if (DOMAIN_SCALE != 1.0) {
      scaleDomainLateral((MeshPart) domain);
    }

    // Before the rotation: cube_solid and domain_box each carry a single default "Block Surface".
    // Split each by 89 deg so every axis-aligned face of the (still unrotated) box becomes its own
    // PartSurface, then rename by direction.
    splitAndNameBoxFaces((MeshPart) cube, CUBE_FACE_NAMES);
    splitAndNameBoxFaces((MeshPart) domain, DOMAIN_FACE_NAMES);

    // Transform, as recorded: no axis or coordinate-system call, the GUI defaults were x and
    // Laboratory when the rotation was set by hand. refine_wake is an input from the start.
    TransformPartsOperation transform = (TransformPartsOperation) ops.createTransformPartsOperation(
      new ArrayList<>(Arrays.<GeometryPart>asList(cube, wake)));
    transform.setPresentationName(TRANSFORM_OP);
    RotationControl rotation = transform.getTransforms().createRotationControl();
    Units deg = (Units) sim.getUnitsManager().getObject("deg");
    rotation.getAngle().setValueAndUnits(ROT_DEG, deg);

    // Tilt, cube_solid only (refine_wake follows the axial flow). Sign: rotating the body by -b about
    // a = (0, -sin phi, cos phi) is, seen from the body, the flow +x rotated by +b about a; by
    // Rodrigues, a x (+x) = (0, cos phi, sin phi), so the relative flow is
    // (cos b, sin b cos phi, sin b sin phi), the same d as inflowDirection(). Assumes a positive angle
    // turns by the right-hand rule about the axis vector.
    TransformPartsOperation tilt = null;
    double[] tiltAxis = null;
    if (TILT_DEG != 0.0) {
      double phi = Math.toRadians(TILT_PHI_DEG);
      tiltAxis = new double[] {0.0, -Math.sin(phi), Math.cos(phi)};
      tilt = (TransformPartsOperation) ops.createTransformPartsOperation(
        new ArrayList<>(Arrays.<GeometryPart>asList(cube)));
      tilt.setPresentationName(TILT_OP);
      RotationControl tiltRotation = tilt.getTransforms().createRotationControl();
      tiltRotation.getAxisVector().setComponents(tiltAxis[0], tiltAxis[1], tiltAxis[2]);
      Units m = (Units) sim.getUnitsManager().getObject("m");
      tiltRotation.getOrigin().setCoordinate(m, m, m,
        new DoubleVector(new double[] {CUBE_CENTRE[0], CUBE_CENTRE[1], CUBE_CENTRE[2]}));
      tiltRotation.getAngle().setValueAndUnits(-TILT_DEG, deg);
    }

    // Subtract, as recorded: the transforms work in place, so cube_solid here is the rotated (and
    // tilted) cube.
    SubtractPartsOperation subtract = (SubtractPartsOperation) ops.createSubtractPartsOperation(
      new ArrayList<>(Arrays.<GeometryPart>asList(cube, domain)));
    subtract.setPresentationName(SUBTRACT_OP);
    subtract.getTargetPartManager().setQuery(null);
    subtract.getTargetPartManager().setObjects(domain);
    subtract.setPerformCADBoolean(true);

    transform.execute();
    sim.println("sym_rot30: " + TRANSFORM_OP + " executed, " + ROT_DEG + " deg about x on "
      + CUBE_PART + ", " + WAKE_PART);
    if (tilt != null) {
      tilt.execute();
      sim.println("sym_rot30: " + TILT_OP + " executed, beta " + TILT_DEG + " deg, phi " + TILT_PHI_DEG
        + " deg on " + CUBE_PART + ": angle " + (-TILT_DEG) + " deg about axis [" + tiltAxis[0] + ", "
        + tiltAxis[1] + ", " + tiltAxis[2] + "], origin [" + CUBE_CENTRE[0] + ", " + CUBE_CENTRE[1] + ", "
        + CUBE_CENTRE[2] + "] m");
    } else {
      sim.println("sym_rot30: no tilt (TILT_DEG = 0)");
    }
    subtract.execute();
    if (parts.hasObject(SUBTRACT_OUTPUT) == null) {
      throw new RuntimeException("sym_rot30: " + SUBTRACT_OP + " left no part '" + SUBTRACT_OUTPUT
        + "'; parts:" + names(parts.getObjects()));
    }
    MeshOperationPart fluidRot = (MeshOperationPart) parts.getPart(SUBTRACT_OUTPUT);
    fluidRot.setPresentationName(FLUID_PART);
    sim.println("sym_rot30: " + SUBTRACT_OP + " executed, output renamed " + FLUID_PART);

    assignRegion(oldFluid, fluidRot);
    AutoMeshOperation mesh = copyBaseMesh(ops, oldFluid, fluidRot);

    mesh.execute();
    long cells = ((FvRepresentation) sim.getRepresentationManager().getDefaultFvRepresentation()).getCellCount();
    sim.println("sym_rot30: " + MESH_OP + " executed, cells " + cells);
    if ((ROT_DEG != 0.0 || TILT_DEG != 0.0) && cells == BASE_CELLS) {
      throw new RuntimeException("sym_rot30: cell count is still the base " + BASE_CELLS
        + "; the rotated or tilted geometry did not reach the mesh.");
    }

    ensureCyMonitor();
    runTransient();
  }

  // The macro runs on a clean copy of cube_re0300.sim: any name it is about to create must be free.
  private void checkFreeNames() {
    SimulationPartManager parts = sim.get(SimulationPartManager.class);
    for (String n : new String[] {SUBTRACT_OUTPUT, FLUID_PART}) {
      if (parts.hasObject(n) != null) {
        throw new RuntimeException("sym_rot30: part '" + n + "' already exists; run on a clean copy.");
      }
    }
    MeshOperationManager ops = sim.get(MeshOperationManager.class);
    for (String n : new String[] {TRANSFORM_OP, TILT_OP, SUBTRACT_OP, MESH_OP, BASE_MESH + " copy"}) {
      if (ops.hasObject(n) != null) {
        throw new RuntimeException("sym_rot30: operation '" + n + "' already exists; run on a clean copy.");
      }
    }
  }

  // Corners read as the min/max extents of domain_box's single "Block Surface" (no SimpleBlockPart corner
  // getter is confirmed in this repo), then rewritten with the confirmed setter setCorners, y and z
  // scaled by DOMAIN_SCALE. Assumes the part's corner units are m, as the extents (SI).
  private void scaleDomainLateral(MeshPart box) {
    List<PartSurface> one = new ArrayList<PartSurface>(box.getPartSurfaces());
    if (one.size() != 1) {
      throw new RuntimeException("sym_rot30: '" + box.getPresentationName() + "' should have one surface before "
        + "scaling; surfaces:" + names(one));
    }
    double[][] lo = new double[1][3];
    double[][] hi = new double[1][3];
    faceExtents(box, one, lo, hi);
    sim.println("sym_rot30: " + DOMAIN_PART + " corners before [" + lo[0][0] + ", " + lo[0][1] + ", " + lo[0][2]
      + "] [" + hi[0][0] + ", " + hi[0][1] + ", " + hi[0][2] + "] m");
    ((SimpleBlockPart) box).setCorners(
      new DoubleVector(lo[0][0], DOMAIN_SCALE * lo[0][1], DOMAIN_SCALE * lo[0][2]),
      new DoubleVector(hi[0][0], DOMAIN_SCALE * hi[0][1], DOMAIN_SCALE * hi[0][2]));
    one = new ArrayList<PartSurface>(box.getPartSurfaces());
    faceExtents(box, one, lo, hi);
    sim.println("sym_rot30: " + DOMAIN_PART + " corners after (DOMAIN_SCALE " + DOMAIN_SCALE + ") [" + lo[0][0]
      + ", " + lo[0][1] + ", " + lo[0][2] + "] [" + hi[0][0] + ", " + hi[0][1] + ", " + hi[0][2] + "] m");
  }

  // Relative tolerance (fraction of the box's own extent on that axis) under which a face's
  // Position span on an axis counts as flat, i.e. that axis is the face's outward normal.
  private static final double FACE_FLAT_TOLERANCE = 1e-6;

  // Splits the part's single default "Block Surface" by FACE_SPLIT_ANGLE so each axis-aligned face
  // becomes its own PartSurface, then renames each by its outward direction (order in `names`:
  // -x,+x,+y,-y,+z,-z). Faces are matched to directions by their own flat axis and sign (faceExtents),
  // not by split order (confirmed unreliable: splitPartSurfacesByAngle's output order is not documented
  // and was observed to vary) and not by AreaAverageReport centroid (confirmed broken: it evaluates on
  // the volume mesh, which does not exist yet at this point in the macro, and returns NaN).
  private void splitAndNameBoxFaces(MeshPart box, String[] names) {
    PartSurfaceManager mgr = box.getPartSurfaceManager();
    Collection<PartSurface> before = box.getPartSurfaces();
    mgr.splitPartSurfacesByAngle(new ArrayList<PartSurface>(before), FACE_SPLIT_ANGLE);
    List<PartSurface> after = new ArrayList<PartSurface>(box.getPartSurfaces());
    if (after.size() != names.length) {
      throw new RuntimeException("sym_rot30: splitting '" + box.getPresentationName() + "' by "
        + FACE_SPLIT_ANGLE + " deg gave " + after.size() + " surfaces, expected " + names.length
        + "; surfaces:" + names(after));
    }

    double[][] lo = new double[after.size()][3];
    double[][] hi = new double[after.size()][3];
    faceExtents(box, after, lo, hi);

    // Box extent per axis, from the faces' own min/max: every face of an axis-aligned box touches one
    // of the box's two bounds on each axis, so the union of all faces' extents is the box's extent.
    // Used both for the flat-axis tolerance and as the center that decides a flat face's sign.
    //
    // Note (API not found documented or used anywhere in this repo, nor shown by the recording): the
    // macro spec asked for the sign to come from "corners del SimpleBlockPart". SimpleBlockPart's only
    // corner API confirmed in this repo is the setter SimpleBlockPart.setCorners(DoubleVector,
    // DoubleVector); no corner getter (e.g. getCorner1()/getCorner2())
    // has a repo call site or recording example, so inventing its signature would guess rather than
    // confirm. The box center is computed from the faces' own extents instead, which needs no
    // additional API and is numerically equivalent to the corner midpoint. If a getCorner-based
    // approach is still wanted, confirm the exact accessor first (e.g. a fresh GUI recording) and swap
    // it in here.
    double[] boxLo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
    double[] boxHi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
    for (int i = 0; i < after.size(); i++) {
      for (int a = 0; a < 3; a++) {
        boxLo[a] = Math.min(boxLo[a], lo[i][a]);
        boxHi[a] = Math.max(boxHi[a], hi[i][a]);
      }
    }
    double[] center = new double[3];
    for (int a = 0; a < 3; a++) {
      center[a] = 0.5 * (boxLo[a] + boxHi[a]);
    }

    // Slot order fixed to names: -x,+x,+y,-y,+z,-z.
    int[] axis = {0, 0, 1, 1, 2, 2};
    boolean[] wantMax = {false, true, true, false, true, false};
    String[] axisLabel = {"x", "x", "y", "y", "z", "z"};
    PartSurface[] bySlot = new PartSurface[names.length];

    for (int i = 0; i < after.size(); i++) {
      PartSurface face = after.get(i);
      int flatAxis = -1;
      for (int a = 0; a < 3; a++) {
        double span = hi[i][a] - lo[i][a];
        double tol = FACE_FLAT_TOLERANCE * Math.max(boxHi[a] - boxLo[a], 1.0);
        if (Math.abs(span) < tol) {
          if (flatAxis != -1) {
            throw new RuntimeException("sym_rot30: '" + box.getPresentationName() + "' face '"
              + face.getPresentationName() + "' is flat on more than one axis (spans "
              + (hi[i][0] - lo[i][0]) + "," + (hi[i][1] - lo[i][1]) + "," + (hi[i][2] - lo[i][2]) + ")");
          }
          flatAxis = a;
        }
      }
      if (flatAxis == -1) {
        throw new RuntimeException("sym_rot30: '" + box.getPresentationName() + "' face '"
          + face.getPresentationName() + "' has no flat axis (spans "
          + (hi[i][0] - lo[i][0]) + "," + (hi[i][1] - lo[i][1]) + "," + (hi[i][2] - lo[i][2]) + ")");
      }
      double value = 0.5 * (lo[i][flatAxis] + hi[i][flatAxis]);
      boolean positive = value > center[flatAxis];

      int slot = -1;
      for (int s = 0; s < names.length; s++) {
        if (axis[s] == flatAxis && wantMax[s] == positive) {
          slot = s;
          break;
        }
      }
      if (slot == -1) {
        throw new RuntimeException("sym_rot30: '" + box.getPresentationName() + "' face '"
          + face.getPresentationName() + "' matched no expected direction (axis " + axisLabel[flatAxis]
          + ", value " + value + ", center " + center[flatAxis] + ")");
      }
      if (bySlot[slot] != null) {
        throw new RuntimeException("sym_rot30: '" + box.getPresentationName() + "' faces '"
          + bySlot[slot].getPresentationName() + "' and '" + face.getPresentationName()
          + "' both resolved to direction '" + names[slot] + "'");
      }
      bySlot[slot] = face;
      sim.println("sym_rot30: '" + box.getPresentationName() + "' face '" + face.getPresentationName()
        + "' -> " + names[slot] + " (axis " + axisLabel[slot] + ", " + (positive ? "+" : "-")
        + ", value " + value + ")");
    }

    for (int slot = 0; slot < names.length; slot++) {
      if (bySlot[slot] == null) {
        throw new RuntimeException("sym_rot30: '" + box.getPresentationName() + "' no face resolved to '"
          + names[slot] + "'");
      }
      bySlot[slot].setPresentationName(names[slot]);
    }
  }

  // Min and max of Position's X/Y/Z component functions, per face, read with the part's "Geometry"
  // representation instead of the default volume-mesh representation: at this point in the macro
  // (before the Transform) no volume mesh exists yet, so an AreaAverageReport here returns NaN. Fix per
  // recordings/GRABACION_2026-10-25_sym_decompose_surface.java, which builds a MaxReport on Position's
  // component 0 the same way: ReportManager.create("star.base.report.MaxReport") (and, by the same
  // string-create pattern, "star.base.report.MinReport"), FieldFunctionManager.getFunction("Position")
  // cast to PrimitiveFieldFunction, getComponentFunction(i) cast to VectorComponentFieldFunction,
  // getParts().setQuery(null) then getParts().setObjects(surface), and
  // setRepresentation((PartRepresentation) RepresentationManager.getObject("Geometry")). Reports are
  // transient and removed in the finally block via ReportManager.remove(ClientServerObject), the same
  // pattern the prior faceCentroids used for AreaAverageReport.
  private void faceExtents(MeshPart box, List<PartSurface> surfaces, double[][] lo, double[][] hi) {
    PrimitiveFieldFunction position =
      (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
    ReportManager rm = sim.getReportManager();
    PartRepresentation geometry =
      (PartRepresentation) sim.getRepresentationManager().getObject("Geometry");
    List<ScalarReport> created = new ArrayList<ScalarReport>();
    try {
      for (int a = 0; a < 3; a++) {
        VectorComponentFieldFunction component =
          (VectorComponentFieldFunction) position.getComponentFunction(a);
        for (int i = 0; i < surfaces.size(); i++) {
          PartSurface face = surfaces.get(i);

          ScalarReport minR = rm.create("star.base.report.MinReport");
          minR.setPresentationName("sym_rot30_tmp_min_" + box.getPresentationName() + "_" + a + "_" + i);
          minR.setFieldFunction(component);
          minR.getParts().setQuery(null);
          minR.getParts().setObjects(face);
          minR.setRepresentation(geometry);
          created.add(minR);

          ScalarReport maxR = rm.create("star.base.report.MaxReport");
          maxR.setPresentationName("sym_rot30_tmp_max_" + box.getPresentationName() + "_" + a + "_" + i);
          maxR.setFieldFunction(component);
          maxR.getParts().setQuery(null);
          maxR.getParts().setObjects(face);
          maxR.setRepresentation(geometry);
          created.add(maxR);

          lo[i][a] = minR.getValue(face);
          hi[i][a] = maxR.getValue(face);
        }
      }
    } finally {
      for (ScalarReport r : created) {
        rm.remove(r);
      }
    }
  }

  private GeometryPart part(String name) {
    SimulationPartManager parts = sim.get(SimulationPartManager.class);
    if (parts.hasObject(name) == null) {
      throw new RuntimeException("sym_rot30: no geometry part '" + name + "'; parts:" + names(parts.getObjects()));
    }
    return parts.getPart(name);
  }

  // fluid_rot30 joins the region next to fluid, every boundary swaps its fluid part surfaces for the
  // fluid_rot30 ones with the same name, then fluid leaves the region. Boundaries are never left empty,
  // so their types, conditions and the force reports on cube_* keep pointing at the same objects.
  private void assignRegion(GeometryPart oldFluid, GeometryPart fluidRot) {
    Region region = sim.getRegionManager().getRegion(REGION);
    Collection<?> inRegion = region.getPartGroup().getObjects();
    if (inRegion.size() != 1 || !inRegion.contains(oldFluid)) {
      throw new RuntimeException("sym_rot30: region '" + REGION + "' should hold only part '"
        + OLD_FLUID_PART + "'; holds:" + names(inRegion));
    }
    region.getPartGroup().setQuery(null);
    region.getPartGroup().setObjects(oldFluid, fluidRot);

    Map<String, PartSurface> byName = surfacesByName(fluidRot);
    Collection<PartSurface> oldSurfaces = oldFluid.getPartSurfaces();
    Set<PartSurface> assigned = new HashSet<PartSurface>();
    for (Boundary b : new ArrayList<Boundary>(region.getBoundaryManager().getObjects())) {
      List<PartSurface> mapped = new ArrayList<PartSurface>();
      for (PartSurface ps : b.getPartSurfaceGroup().getObjects()) {
        if (oldSurfaces.contains(ps)) {
          mapped.add(match(byName, ps.getPresentationName(), "boundary '" + b.getPresentationName() + "'"));
        }
      }
      if (mapped.isEmpty()) {
        continue;
      }
      b.getPartSurfaceGroup().setQuery(null);
      b.getPartSurfaceGroup().setObjects(mapped.toArray(new PartSurface[0]));
      assigned.addAll(mapped);
      sim.println("sym_rot30: boundary '" + b.getPresentationName() + "' -> " + names(mapped));
    }

    region.getPartGroup().setQuery(null);
    region.getPartGroup().setObjects(fluidRot);
    if (assigned.size() != byName.size()) {
      List<String> left = new ArrayList<String>();
      for (Map.Entry<String, PartSurface> e : byName.entrySet()) {
        if (!assigned.contains(e.getValue())) {
          left.add(e.getKey());
        }
      }
      throw new RuntimeException("sym_rot30: part surfaces of " + FLUID_PART + " in no former boundary: " + left);
    }
    sim.println("sym_rot30: region '" + REGION + "' now holds " + FLUID_PART + " only");
  }

  // Copy of mesh_cube as recorded (createCopyOfMeshOperation + copyProperties), input fluid_rot30.
  // wake_refinement keeps refine_wake (rotated in place); surface controls move to fluid_rot30 by name.
  private AutoMeshOperation copyBaseMesh(MeshOperationManager ops, GeometryPart oldFluid, GeometryPart fluidRot) {
    if (ops.hasObject(BASE_MESH) == null) {
      throw new RuntimeException("sym_rot30: no mesh operation '" + BASE_MESH + "'; operations:" + names(ops.getObjects()));
    }
    AutoMeshOperation base = (AutoMeshOperation) ops.getObject(BASE_MESH);
    ops.createCopyOfMeshOperation("star.meshing.AutoMeshOperation", BASE_MESH + " copy");
    AutoMeshOperation mesh = (AutoMeshOperation) ops.getObject(BASE_MESH + " copy");
    mesh.copyProperties(base);
    mesh.setPresentationName(MESH_OP);
    mesh.getInputGeometryObjects().setQuery(null);
    mesh.getInputGeometryObjects().setObjects(fluidRot);

    CustomMeshControlManager controls = mesh.getCustomMeshControls();
    for (String n : BASE_CONTROLS) {
      if (controls.hasObject(n) == null) {
        throw new RuntimeException("sym_rot30: " + MESH_OP + " has no control '" + n + "' after the copy; controls:"
          + names(controls.getObjects()));
      }
    }
    Map<String, PartSurface> byName = surfacesByName(fluidRot);
    Collection<PartSurface> oldSurfaces = oldFluid.getPartSurfaces();
    for (Object o : controls.getObjects()) {
      if (!(o instanceof SurfaceCustomMeshControl)) {
        continue;
      }
      SurfaceCustomMeshControl ctrl = (SurfaceCustomMeshControl) o;
      SurfaceCustomMeshControl baseCtrl =
        (SurfaceCustomMeshControl) base.getCustomMeshControls().getObject(ctrl.getPresentationName());
      List<PartSurface> mapped = new ArrayList<PartSurface>();
      for (Object g : baseCtrl.getGeometryObjects().getObjects()) {
        if (!oldSurfaces.contains(g)) {
          throw new RuntimeException("sym_rot30: control '" + ctrl.getPresentationName()
            + "' holds an object outside part '" + OLD_FLUID_PART + "': " + ((NamedObject) g).getPresentationName());
        }
        mapped.add(match(byName, ((PartSurface) g).getPresentationName(), "control '" + ctrl.getPresentationName() + "'"));
      }
      if (mapped.isEmpty()) {
        throw new RuntimeException("sym_rot30: control '" + ctrl.getPresentationName() + "' has no part surfaces.");
      }
      ctrl.getGeometryObjects().setQuery(null);
      ctrl.getGeometryObjects().setObjects(mapped.toArray(new PartSurface[0]));
      sim.println("sym_rot30: " + MESH_OP + " control '" + ctrl.getPresentationName() + "' -> " + names(mapped));
    }
    sim.println("sym_rot30: " + MESH_OP + " = copy of " + BASE_MESH + ", input " + FLUID_PART);
    return mesh;
  }

  private Map<String, PartSurface> surfacesByName(GeometryPart p) {
    Map<String, PartSurface> byName = new TreeMap<String, PartSurface>();
    for (PartSurface ps : p.getPartSurfaces()) {
      if (byName.put(ps.getPresentationName(), ps) != null) {
        throw new RuntimeException("sym_rot30: two part surfaces named '" + ps.getPresentationName()
          + "' in " + p.getPresentationName());
      }
    }
    return byName;
  }

  // Same name, or else the only surface whose name after the last '.' matches (the Subtract output
  // may prefix the input part name). Nothing else is guessed.
  private PartSurface match(Map<String, PartSurface> byName, String oldName, String where) {
    PartSurface exact = byName.get(oldName);
    if (exact != null) {
      return exact;
    }
    String leaf = oldName.substring(oldName.lastIndexOf('.') + 1);
    PartSurface found = null;
    for (Map.Entry<String, PartSurface> e : byName.entrySet()) {
      if (e.getKey().substring(e.getKey().lastIndexOf('.') + 1).equals(leaf)) {
        if (found != null) {
          throw new RuntimeException("sym_rot30: " + where + ": '" + oldName + "' matches more than one surface of "
            + FLUID_PART + ": " + byName.keySet());
        }
        found = e.getValue();
      }
    }
    if (found == null) {
      throw new RuntimeException("sym_rot30: " + where + ": no surface of " + FLUID_PART + " for '" + oldName
        + "'; surfaces: " + byName.keySet());
    }
    return found;
  }

  private String names(Collection<?> objects) {
    StringBuilder sb = new StringBuilder();
    for (Object o : objects) {
      sb.append(" '").append(((NamedObject) o).getPresentationName()).append("'");
    }
    return sb.toString();
  }

  // Same transient set-up as the aligned case, with a uniform initial velocity.
  // cube_re0300.sim predates the transient set-up that created "cy Monitor", so it may be missing.
  private void ensureCyMonitor() {
    if (sim.getMonitorManager().hasObject("cy Monitor") != null) {
      return;
    }
    Report cy = sim.getReportManager().getReport("cy");
    sim.getMonitorManager().createMonitors(new ArrayList<>(Arrays.<Report>asList(cy)),
      new PlotCreationInfo(PlotCreationInfo.CreatePlotChoice.SINGLE_PLOT, " Plot"));
    sim.println("sym_rot30: created cy Monitor");
  }

  private void runTransient() {
    PhysicsContinuum physics =
      (PhysicsContinuum) sim.getContinuumManager().getContinuum("fluid_physics");
    physics.disableModel(physics.getModelManager().getModel(SteadyModel.class));
    physics.enable(ImplicitUnsteadyModel.class);

    // Note: VelocityProfile/ConstantVectorProfileMethod access confirmed only by Javadoc text
    // (star.flow.VelocityProfile extends VectorProfile; star.common.PhysicsContinuum has
    // getInitialConditions()), not by any call site in this repo. The exact
    // chain from getInitialConditions() to a settable Constant(1.0, 0.0, 0.0) m/s component
    // needs confirming against the GUI recording or a compile round; verify before relying on it.
    double[] d = inflowDirection();
    double uIn = applyInletVelocity(d);
    VelocityProfile velIC = physics.getInitialConditions().get(VelocityProfile.class);
    Units mps = (Units) sim.getUnitsManager().getObject("m/s");
    ((ConstantVectorProfileMethod) velIC.getMethod(ConstantVectorProfileMethod.class))
      .getQuantity().setComponentsAndUnits(uIn * d[0], uIn * d[1], uIn * d[2], mps);
    sim.println("sym_rot30: inflow beta " + INFLOW_DEG + " deg, phi " + INFLOW_PHI_DEG
      + " deg; initial velocity [" + (uIn * d[0]) + ", " + (uIn * d[1]) + ", " + (uIn * d[2]) + "] m/s");

    Units s = (Units) sim.getUnitsManager().getObject("s");
    ImplicitUnsteadySolver solver =
      (ImplicitUnsteadySolver) sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class);
    solver.getTimeStep().setValueAndUnits(DT, s);
    solver.getTimeDiscretizationOption().setSelected(TimeDiscretizationOption.Type.SECOND_ORDER);

    InnerIterationStoppingCriterion inner =
      (InnerIterationStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Inner Iterations");
    inner.getMaxIterations().getQuantity().setValue(10.0);
    PhysicalTimeStoppingCriterion tmax =
      (PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time");
    tmax.getMaximumTime().setValueAndUnits(T_END, s);
    StepStoppingCriterion maxSteps =
      (StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
    maxSteps.setIsUsed(false);

    for (String name : MONITORS) {
      ReportMonitor m = (ReportMonitor) sim.getMonitorManager().getMonitor(name);
      StarUpdate u = m.getStarUpdateStageable().getStarUpdate();
      u.getUpdateModeOption().setSelected(StarUpdateModeOption.Type.TIMESTEP);
      u.getTimeStepUpdateFrequency().getTimeStepFrequencyQuantity().getQuantity().setValue(1.0);
    }

    SimulationIterator it = sim.getSimulationIterator();
    StringBuilder csv = new StringBuilder("tU_D,cd,cl,cy\n");
    File csvFile = new File(sim.getSessionDirFile(), "sym_rot30_transient.csv");
    for (int k = 1; k <= STEPS; k++) {
      it.step(1, true);
      csv.append(k * DT).append(',').append(value("cd")).append(',').append(value("cl"))
        .append(',').append(value("cy")).append('\n');
      if (k % 500 == 0) {
        writeTextFile(csvFile, csv.toString());
        sim.println("sym_rot30: tU_D " + (k * DT));
      }
    }
    writeTextFile(csvFile, csv.toString());
    sim.saveState(new File(sim.getSessionDirFile(), "sym_rot30_cube_re0300_t725.sim").getAbsolutePath());
  }

  private double[] inflowDirection() {
    double b = Math.toRadians(INFLOW_DEG);
    double phi = Math.toRadians(INFLOW_PHI_DEG);
    return new double[] {Math.cos(b), Math.sin(b) * Math.cos(phi), Math.sin(b) * Math.sin(phi)};
  }

  // Reads U (SI) from the inlet of region fluid with the method it already uses: COMPONENTS (U = |v|
  // of the constant VelocityProfile) or MAGNITUDE_DIRECTION (constant VelocityMagnitudeProfile, left
  // as is; only FlowDirectionProfile changes). Sets U*d unless INFLOW_DEG is 0. Returns U.
  private double applyInletVelocity(double[] d) {
    BoundaryManager bm = sim.getRegionManager().getRegion(REGION).getBoundaryManager();
    if (bm.hasObject(INLET) == null) {
      throw new RuntimeException("sym_rot30: region '" + REGION + "' has no boundary '" + INLET
        + "'; boundaries:" + names(bm.getObjects()));
    }
    Boundary inlet = bm.getBoundary(INLET);
    InletVelocityOption.Type method =
      inlet.getConditions().get(InletVelocityOption.class).getSelectedElement();
    boolean apply = INFLOW_DEG != 0.0;
    double u;
    if (method == InletVelocityOption.Type.COMPONENTS) {
      Object m = inlet.getValues().get(VelocityProfile.class).getMethod();
      if (!(m instanceof ConstantVectorProfileMethod)) {
        throw new RuntimeException("sym_rot30: inlet velocity is not a constant vector: " + m);
      }
      VectorPhysicalQuantity v = ((ConstantVectorProfileMethod) m).getQuantity();
      DoubleVector now = v.getInternalVector();
      u = Math.sqrt(now.get(0) * now.get(0) + now.get(1) * now.get(1) + now.get(2) * now.get(2));
      if (apply) {
        v.setComponentsAndUnits(u * d[0], u * d[1], u * d[2], (Units) sim.getUnitsManager().getObject("m/s"));
      }
    } else if (method == InletVelocityOption.Type.MAGNITUDE_DIRECTION) {
      Object mag = inlet.getValues().get(VelocityMagnitudeProfile.class).getMethod();
      Object dir = inlet.getValues().get(FlowDirectionProfile.class).getMethod();
      if (!(mag instanceof ConstantScalarProfileMethod) || !(dir instanceof ConstantVectorProfileMethod)) {
        throw new RuntimeException("sym_rot30: inlet magnitude/direction not constant: " + mag + ", " + dir);
      }
      u = ((ConstantScalarProfileMethod) mag).getQuantity().getSIValue();
      if (apply) {
        ((ConstantVectorProfileMethod) dir).getQuantity().setComponents(d[0], d[1], d[2]);
      }
    } else {
      throw new RuntimeException("sym_rot30: inlet velocity option " + method + " not handled");
    }
    sim.println("sym_rot30: inlet " + method + ", U " + u + " m/s, "
      + (apply ? "set to [" + (u * d[0]) + ", " + (u * d[1]) + ", " + (u * d[2]) + "] m/s"
               : "left as in the .sim (INFLOW_DEG = 0)"));
    return u;
  }

  // Report.monitoredValue() returns SI (run_macro.java, bench-verified).
  private double value(String reportName) {
    return sim.getReportManager().getReport(reportName).monitoredValue();
  }

  private void writeTextFile(File f, String content) {
    BufferedWriter w = null;
    try {
      w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
      w.write(content);
    } catch (Exception e) {
      throw new RuntimeException("sym_rot30: could not write '" + f.getName() + "': " + e.getMessage());
    } finally {
      if (w != null) {
        try {
          w.close();
        } catch (Exception ignored) {
          // nothing to do
        }
      }
    }
  }
}
