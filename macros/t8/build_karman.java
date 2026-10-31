// build_karman.java
// Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1, mu = 0.01).
// Builds a thin 3D domain (z in [0, T_SPAN]), its fine 3D mesh, physics and initial condition;
// saves without running. The 2D case comes later from Mesh > Convert to 2D on the z = 0 cell faces.

import java.util.*;

import star.common.*;
import star.base.report.*;
import star.metrics.*;
import star.base.neo.*;
import star.meshing.*;
import star.prismmesher.*;
import star.trimmer.*;
import star.material.*;
import star.flow.*;
import star.segregatedflow.*;
import star.coremodule.ui.AssignPartsToRegionsDialog;

public class build_karman extends StarMacro {

    // Trimmer sizes can only be MAX_CELL / 2^n; every mesh size below is written that way.
    private static final double MAX_CELL = 0.8;
    private static final double BASE_SIZE = MAX_CELL;
    private static final double WAKE_SIZE = MAX_CELL / 16.0;      // 0.05
    private static final double CYLINDER_SIZE = MAX_CELL / 32.0;  // 0.025

    // Span of the extruded domain (one trimmer cell); z = 0 and z = T_SPAN are symmetry planes.
    private static final double T_SPAN = MAX_CELL;

    public void execute() {

        Simulation sim = getActiveSimulation();
        UnitsManager units = sim.getUnitsManager();

        Units ul = ensureUnits(units, "m", Dimensions.Builder().length(1).build());
        Units ud = ensureUnits(units, "kg/m^3", Dimensions.Builder().mass(1).volume(-1).build());
        Units uv = ensureUnits(units, "Pa-s", Dimensions.Builder().pressure(1).time(1).build());
        Units uu = ensureUnits(units, "m/s", Dimensions.Builder().velocity(1).build());
        Units up = ensureUnits(units, "Pa", Dimensions.Builder().pressure(1).build());
        Units udim = units.getUnits("");

        // --- Global parameters (non-dimensional: rho = U = D = 1, Re = 100, mu = rho*U*D/Re) ---
        GlobalParameterManager paramMgr = sim.getGlobalParameterManager();

        ScalarGlobalParameter dParam = paramMgr.createGlobalParameter(ScalarGlobalParameter.class, "D");
        ((ScalarGlobalParameter) sim.get(GlobalParameterManager.class).getObject("D"))
            .setDimensions(Dimensions.Builder().length(1).build());
        dParam.getQuantity().setValueAndUnits(1.0, ul);

        ScalarGlobalParameter uParam = paramMgr.createGlobalParameter(ScalarGlobalParameter.class, "U");
        ((ScalarGlobalParameter) sim.get(GlobalParameterManager.class).getObject("U"))
            .setDimensions(Dimensions.Builder().length(1).time(-1).build());
        uParam.getQuantity().setValueAndUnits(1.0, uu);

        ScalarGlobalParameter rhoParam = paramMgr.createGlobalParameter(ScalarGlobalParameter.class, "rho");
        ((ScalarGlobalParameter) sim.get(GlobalParameterManager.class).getObject("rho"))
            .setDimensions(Dimensions.Builder().mass(1).length(-3).build());
        rhoParam.getQuantity().setValueAndUnits(1.0, ud);

        ScalarGlobalParameter reParam = paramMgr.createGlobalParameter(ScalarGlobalParameter.class, "Re");
        reParam.setDimensions(new Dimensions());
        reParam.getQuantity().setValueAndUnits(100.0, udim);

        ScalarGlobalParameter muParam = paramMgr.createGlobalParameter(ScalarGlobalParameter.class, "mu");
        ((ScalarGlobalParameter) sim.get(GlobalParameterManager.class).getObject("mu"))
            .setDimensions(Dimensions.Builder().mass(1).length(-1).time(-1).build());
        muParam.getQuantity().setDefinitionAndUnits("${rho}*${U}*${D}/${Re}", uv);

        // --- Geometry: fluid x/D in [-15, 30], y/D in [-15, 15], z in [0, T_SPAN] ---
        // Each outer face of the fluid comes from its own cutter part, so faces are identified
        // by part-surface name before any region or mesh exists (no boundary extents needed).
        GeometryPartManager partMgr = sim.getGeometryPartManager();
        MeshPartFactory meshPartFactory = sim.get(MeshPartFactory.class);

        // Oversized block: the cutters remove all its faces, so every fluid face carries a cutter token.
        SimpleBlockPart domain = meshPartFactory.createNewBlockPart(partMgr);
        domain.setPresentationName("Domain");
        DoubleVector corner1 = new DoubleVector(new double[] {-16.0, -16.0, -0.5});
        DoubleVector corner2 = new DoubleVector(new double[] {31.0, 16.0, T_SPAN + 0.5});
        domain.setCorner1(corner1);
        domain.setCorner2(corner2);

        SimpleBlockPart cutInlet = createCutter(meshPartFactory, partMgr, "CutInlet", "inlet",
            -20.0, -20.0, -1.0, -15.0, 20.0, 2.0);
        SimpleBlockPart cutOutlet = createCutter(meshPartFactory, partMgr, "CutOutlet", "outlet",
            30.0, -20.0, -1.0, 35.0, 20.0, 2.0);
        SimpleBlockPart cutTop = createCutter(meshPartFactory, partMgr, "CutTop", "symTop",
            -20.0, 15.0, -1.0, 35.0, 20.0, 2.0);
        SimpleBlockPart cutBottom = createCutter(meshPartFactory, partMgr, "CutBottom", "symBottom",
            -20.0, -20.0, -1.0, 35.0, -15.0, 2.0);
        // z = 0 comes whole and planar from one cutter face (Convert to 2D takes the cell faces there).
        SimpleBlockPart cutZ0 = createCutter(meshPartFactory, partMgr, "CutZ0", "plane_z0",
            -20.0, -20.0, -1.0, 35.0, 20.0, 0.0);
        SimpleBlockPart cutZt = createCutter(meshPartFactory, partMgr, "CutZt", "plane_zt",
            -20.0, -20.0, T_SPAN, 35.0, 20.0, 2.0);

        // Cylinder runs through both z planes, so its hole has no caps inside the fluid.
        SimpleCylinderPart cylinder = meshPartFactory.createNewCylinderPart(partMgr);
        cylinder.setPresentationName("Cylinder");
        cylinder.getStartCoordinate().setCoordinate(ul, ul, ul, new DoubleVector(new double[] {0.0, 0.0, -1.0}));
        cylinder.getEndCoordinate().setCoordinate(ul, ul, ul, new DoubleVector(new double[] {0.0, 0.0, 2.0}));
        cylinder.setRadius(0.5);
        for (PartSurface ps : cylinder.getPartSurfaceManager().getPartSurfaces()) {
            ps.setPresentationName("cylinder");
        }

        MeshOperationManager meshOpMgr = sim.get(MeshOperationManager.class);

        Collection<GeometryPart> subtractParts = new ArrayList<GeometryPart>();
        subtractParts.add(domain);
        subtractParts.add(cutInlet);
        subtractParts.add(cutOutlet);
        subtractParts.add(cutTop);
        subtractParts.add(cutBottom);
        subtractParts.add(cutZ0);
        subtractParts.add(cutZt);
        subtractParts.add(cylinder);
        SubtractPartsOperation subtractOp =
            (SubtractPartsOperation) meshOpMgr.createSubtractPartsOperation(subtractParts);
        subtractOp.setTargetPart((MeshPart) domain);
        subtractOp.execute();

        GeometryPart fluidPart = subtractOp.getOutputParts().getObjects().iterator().next();

        // --- Name the faces at part-surface level (inlet, outlet, symmetry, cylinder, plane_z0, plane_zt) ---
        PartSurface inletSurf = findSurface(fluidPart, "inlet");
        PartSurface outletSurf = findSurface(fluidPart, "outlet");
        PartSurface cylinderSurf = findSurface(fluidPart, "cylinder");
        PartSurface z0Surf = findSurface(fluidPart, "plane_z0");
        PartSurface ztSurf = findSurface(fluidPart, "plane_zt");
        List<PartSurface> sideSurfs = new ArrayList<PartSurface>();
        sideSurfs.add(findSurface(fluidPart, "symTop"));
        sideSurfs.add(findSurface(fluidPart, "symBottom"));
        ((MeshOperationPart) fluidPart).combinePartSurfaces(sideSurfs);
        PartSurface symmetrySurf = findSurface(fluidPart, "sym");

        inletSurf.setPresentationName("inlet");
        outletSurf.setPresentationName("outlet");
        symmetrySurf.setPresentationName("symmetry");
        cylinderSurf.setPresentationName("cylinder");
        z0Surf.setPresentationName("plane_z0");
        ztSurf.setPresentationName("plane_zt");

        // --- Region and boundaries: one boundary per part surface, matched by part surface ---
        RegionManager regionMgr = sim.getRegionManager();
        Region region = regionMgr.createEmptyRegion();
        region.setPresentationName("fluid");

        Collection<GeometryPart> regionParts = new ArrayList<GeometryPart>();
        regionParts.add(fluidPart);
        regionMgr.newRegionsFromParts(
            regionParts,
            AssignPartsToRegionsDialog.RegionMode.OneRegion.str(),
            region,
            AssignPartsToRegionsDialog.BoundaryMode.OneBoundaryPerPartSurface.str(),
            null,
            false);

        Boundary inletBnd = findBoundary(region, inletSurf);
        Boundary outletBnd = findBoundary(region, outletSurf);
        Boundary symmetryBnd = findBoundary(region, symmetrySurf);
        Boundary cylinderBnd = findBoundary(region, cylinderSurf);
        Boundary z0Bnd = findBoundary(region, z0Surf);
        Boundary ztBnd = findBoundary(region, ztSurf);

        // --- Physics ---
        PhysicsContinuum physics = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        physics.setPresentationName("Physics");
        physics.enable(ThreeDimensionalModel.class);
        physics.enable(ImplicitUnsteadyModel.class);
        physics.enable(SingleComponentGasModel.class);
        physics.enable(SegregatedFlowModel.class);
        physics.enable(ConstantDensityModel.class);
        physics.enable(LaminarModel.class);

        SingleComponentGasModel gasModel = physics.getModelManager().getModel(SingleComponentGasModel.class);
        Material material = gasModel.getMaterial();

        MaterialPropertyManager propMgr = material.getMaterialProperties();

        ConstantMaterialPropertyMethod densityMethod =
            (ConstantMaterialPropertyMethod) propMgr.getMaterialProperty(ConstantDensityProperty.class).getMethod();
        densityMethod.getQuantity().setDefinitionAndUnits("${rho}", ud);

        ConstantMaterialPropertyMethod viscosityMethod =
            (ConstantMaterialPropertyMethod) propMgr.getMaterialProperty(DynamicViscosityProperty.class).getMethod();
        viscosityMethod.getQuantity().setDefinitionAndUnits("${mu}", uv);

        region.setPhysicsContinuum(physics);

        // --- Boundary types and values (need the region continuum) ---
        inletBnd.setPresentationName("inlet");
        inletBnd.setBoundaryType(InletBoundary.class);
        // Default inlet is magnitude + direction (no VelocityProfile); switch to components.
        inletBnd.getConditions().get(InletVelocityOption.class)
            .setSelected(InletVelocityOption.Type.COMPONENTS);
        VelocityProfile inletVel = inletBnd.getValues().get(VelocityProfile.class);
        inletVel.getMethod(ConstantVectorProfileMethod.class).getQuantity()
            .setDefinitionAndUnits("[${U}, 0, 0]", uu);

        outletBnd.setPresentationName("outlet");
        outletBnd.setBoundaryType(PressureBoundary.class);

        symmetryBnd.setPresentationName("symmetry");
        symmetryBnd.setBoundaryType(SymmetryBoundary.class);

        cylinderBnd.setPresentationName("cylinder");
        cylinderBnd.setBoundaryType(WallBoundary.class);

        z0Bnd.setPresentationName("plane_z0");
        z0Bnd.setBoundaryType(SymmetryBoundary.class);

        ztBnd.setPresentationName("plane_zt");
        ztBnd.setBoundaryType(SymmetryBoundary.class);

        // --- Mesh (3D: surface remesher + trimmer + prism layers) ---
        Collection<GeometryPart> meshParts = new ArrayList<GeometryPart>();
        meshParts.add(fluidPart);
        List<String> meshers = new ArrayList<String>();
        meshers.add("star.resurfacer.ResurfacerAutoMesher");
        meshers.add("star.trimmer.TrimmerAutoMesher");
        meshers.add("star.prismmesher.PrismAutoMesher");
        AutoMeshOperation autoMeshOp = meshOpMgr.createAutoMeshOperation(meshers, meshParts);

        // --- Mesh sizing: base = MAX_CELL, trimmer max cell size left at its default 100 % of base ---
        autoMeshOp.getDefaultValues().get(BaseSize.class).setValueAndUnits(BASE_SIZE, ul);

        CustomMeshControlManager customCtrlMgr = autoMeshOp.getCustomMeshControls();

        // Cylinder surface custom size = MAX_CELL / 32
        SurfaceCustomMeshControl cylCtrl = customCtrlMgr.createSurfaceControl();
        cylCtrl.setPresentationName("Cylinder Surface Size");
        cylCtrl.getGeometryObjects().addParts(cylinderBnd.getPartSurfaceGroup().getObjects());
        cylCtrl.getCustomConditions().get(PartsTargetSurfaceSizeOption.class)
            .setSelected(PartsTargetSurfaceSizeOption.Type.CUSTOM);
        cylCtrl.getCustomValues().get(PartsTargetSurfaceSize.class).setAbsoluteSize(CYLINDER_SIZE, ul);

        // Prism layers on the cylinder only: disable them on every other surface
        SurfaceCustomMeshControl noPrismCtrl = customCtrlMgr.createSurfaceControl();
        noPrismCtrl.setPresentationName("No Prisms");
        noPrismCtrl.getGeometryObjects().addParts(inletBnd.getPartSurfaceGroup().getObjects());
        noPrismCtrl.getGeometryObjects().addParts(outletBnd.getPartSurfaceGroup().getObjects());
        noPrismCtrl.getGeometryObjects().addParts(symmetryBnd.getPartSurfaceGroup().getObjects());
        noPrismCtrl.getGeometryObjects().addParts(z0Bnd.getPartSurfaceGroup().getObjects());
        noPrismCtrl.getGeometryObjects().addParts(ztBnd.getPartSurfaceGroup().getObjects());
        noPrismCtrl.getCustomConditions().get(PartsCustomizePrismMesh.class)
            .getCustomPrismOptions().setSelected(PartsCustomPrismsOption.Type.DISABLE);

        // Wake refinement block: x/D in [-2,15], y/D in [-3,3], volume size = MAX_CELL / 16
        SimpleBlockPart wakeBlock = meshPartFactory.createNewBlockPart(partMgr);
        wakeBlock.setPresentationName("WakeRefinementBlock");
        wakeBlock.setCorner1(new DoubleVector(new double[] {-2.0, -3.0, -0.5}));
        wakeBlock.setCorner2(new DoubleVector(new double[] {15.0, 3.0, T_SPAN + 0.5}));

        VolumeCustomMeshControl wakeCtrl = customCtrlMgr.createVolumeControl();
        wakeCtrl.setPresentationName("Wake Volume Size");
        wakeCtrl.getGeometryObjects().add(wakeBlock);
        wakeCtrl.getCustomConditions().get(VolumeControlTrimmerSizeOption.class)
            .setVolumeSourceTrimmerSizeOption(true);
        wakeCtrl.getCustomValues().get(VolumeControlSize.class).setAbsoluteSize(WAKE_SIZE, ul);

        // Prism layers: 10 layers, total thickness = 0.1; first cell and growth left at default
        autoMeshOp.getDefaultValues().get(NumPrismLayers.class).setNumLayers(10);
        autoMeshOp.getDefaultValues().get(PrismThickness.class).setAbsoluteSize(0.1, ul);

        // --- Generate the mesh here (no other macro meshes) and log the fluid cell count ---
        autoMeshOp.execute();
        ElementCountReport cellCount = sim.getReportManager().createReport(ElementCountReport.class);
        cellCount.setPresentationName("cell_count");
        cellCount.getParts().setObjects(region);
        sim.println("build_karman: 3D fluid cells = " + (long) cellCount.getReportMonitorValue());

        // --- Solver time discretization ---
        ImplicitUnsteadySolver timeSolver =
            (ImplicitUnsteadySolver) sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class);
        timeSolver.getTimeDiscretizationOption().setSelected(TimeDiscretizationOption.Type.SECOND_ORDER);

        // --- Initial condition: u = 1, v = 0.01 to break symmetry ---
        VelocityProfile velIC = physics.getInitialConditions().get(VelocityProfile.class);
        velIC.getMethod(ConstantVectorProfileMethod.class).getQuantity()
            .setComponentsAndUnits(1.0, 0.01, 0.0, uu);

        // --- Save the 3D case without running ---
        sim.saveState(sim.getSessionDir() + java.io.File.separator + "karman_3d.sim");

        // --- Convert to 2D on the z = 0 cell faces (recorded call); yields "fluid 2D" with a new continuum ---
        sim.getMeshManager().convertTo2d(1.005E-6,
            new ArrayList<>(Arrays.<Region>asList(sim.getRegionManager().getRegion("fluid"))), true);

        Region region2d = sim.getRegionManager().getRegion("fluid 2D");
        PhysicsContinuum physics2d = (PhysicsContinuum) region2d.getPhysicsContinuum();
        physics2d.setPresentationName("Physics 2D");
        region2d.setPresentationName("fluid");

        // The 3D continuum is left without a region: remove it.
        sim.getContinuumManager().removeObjects(new ArrayList<>(Arrays.<Continuum>asList(physics)));

        // Converted 2D is treated as unit depth: coefficient reference area = D * 1.
        cellCount.getParts().setObjects(region2d);
        sim.println("build_karman: 2D fluid cells = " + (long) cellCount.getReportMonitorValue());

        sim.saveState(sim.getSessionDir() + java.io.File.separator + "karman_re100.sim");
    }

    // TODO-2D: not called in the 3D build; 2D physics goes back in after Mesh > Convert to 2D.
    private void enable2dPhysics(PhysicsContinuum physics) {
        physics.enable(TwoDimensionalModel.class);
    }

    // Cutter block; its single part surface carries the face token.
    private SimpleBlockPart createCutter(MeshPartFactory f, GeometryPartManager pm, String name, String token,
                                         double x0, double y0, double z0, double x1, double y1, double z1) {
        SimpleBlockPart b = f.createNewBlockPart(pm);
        b.setPresentationName(name);
        b.setCorner1(new DoubleVector(new double[] {x0, y0, z0}));
        b.setCorner2(new DoubleVector(new double[] {x1, y1, z1}));
        for (PartSurface ps : b.getPartSurfaceManager().getPartSurfaces()) {
            ps.setPresentationName(token);
        }
        return b;
    }

    // Exactly one part surface of the part whose name contains the token.
    private PartSurface findSurface(GeometryPart part, String token) {
        PartSurface found = null;
        StringBuilder names = new StringBuilder();
        for (PartSurface ps : part.getPartSurfaces()) {
            names.append(" [").append(ps.getPresentationName()).append("]");
            if (ps.getPresentationName().contains(token)) {
                if (found != null) {
                    throw new RuntimeException("Two part surfaces match '" + token + "':" + names);
                }
                found = ps;
            }
        }
        if (found == null) {
            throw new RuntimeException("No part surface matches '" + token + "':" + names);
        }
        return found;
    }

    private Boundary findBoundary(Region region, PartSurface surf) {
        for (Boundary bnd : region.getBoundaryManager().getBoundaries()) {
            if (bnd.getPartSurfaceGroup().getObjects().contains(surf)) {
                return bnd;
            }
        }
        throw new RuntimeException("No boundary holds part surface " + surf.getPresentationName());
    }

    private Units ensureUnits(UnitsManager units, String name, Dimensions dims) {
        if (units.has(name)) {
            return units.getUnits(name);
        }
        UserUnits newUnits = units.createUnits(name, false);
        newUnits.setDescription(name);
        newUnits.setDimensions(dims);
        newUnits.setConversion(1.0);
        return newUnits;
    }
}
