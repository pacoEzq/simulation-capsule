// setup_monitors.java
// Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1, mu = 0.01).
// Runs on the karman_re100.sim left by build_karman (region fluid, continuum Physics 2D,
// boundaries cylinder, inlet, outlet, symmetry); creates no region or physics.
// Reports and monitors of the case, sampled every time step: cd and cl on the cylinder,
// point probes p1 (2, 0) and p2 (5, 0.5) in x/D, y/D with u and v, and the time report tU_D.
// Field statistics: mean and variance of u, v and p on fluid from tU/D = 100 (window [100, 300]),
// and the non-dimensional field functions u_over_U_mean/rms, v_over_U_mean/rms, cp_mean/rms.
// RMS is sqrt(variance) of the fluctuation, never the raw RMS of the field.
// Every object is looked up by name first and reused if present, so the macro can be rerun.

import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.ForceCoefficientReport;
import star.vis.PointPart;

public class setup_monitors extends StarMacro {

    public void execute() {

        Simulation sim = getActiveSimulation();
        UnitsManager units = sim.getUnitsManager();

        Units uu = ensureUnits(units, "m/s", Dimensions.Builder().velocity(1).build());
        Units ul = ensureUnits(units, "m", Dimensions.Builder().length(1).build());
        Units ud = ensureUnits(units, "kg/m^3", Dimensions.Builder().mass(1).volume(-1).build());
        Units ua = ensureUnits(units, "m^2", Dimensions.Builder().length(2).build());

        Region region = sim.getRegionManager().getRegion("fluid");
        Boundary cylinderBnd = region.getBoundaryManager().getBoundary("cylinder");

        // --- Force coefficients on the cylinder: cd (drag, x) and cl (lift, y); area = D * 1 (unit depth) ---
        forceCoefficient(sim, "cd", cylinderBnd, 1.0, 0.0, ud, uu, ua, ul);
        forceCoefficient(sim, "cl", cylinderBnd, 0.0, 1.0, ud, uu, ua, ul);

        // --- Velocity components ---
        PrimitiveFieldFunction velocityFF =
            (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Velocity");
        FieldFunction uFF = velocityFF.getComponentFunction(0);
        FieldFunction vFF = velocityFF.getComponentFunction(1);

        // --- Point probes: x = (x/D)*D, y = (y/D)*D with D read from the global parameter D ---
        double d = parameter(sim, "D");
        double uRef = parameter(sim, "U");
        if (Math.abs(d - 1.0) > 1.0e-12) {
            sim.println("setup_monitors: WARNING D = " + d + " (spec uses D = 1); p1, p2 scaled by D.");
        }
        PointPart p1 = pointPart(sim, "p1", region, P1_XD * d, P1_YD * d, ul);
        PointPart p2 = pointPart(sim, "p2", region, P2_XD * d, P2_YD * d, ul);

        pointProbe(sim, "p1_u", uFF, p1);
        pointProbe(sim, "p1_v", vFF, p1);
        pointProbe(sim, "p2_u", uFF, p2);
        pointProbe(sim, "p2_v", vFF, p2);

        // --- Time column for export_signals, sampled with the other monitors: tU_D = t*U/D ---
        timeReport(sim, "tU_D");

        // --- Field statistics from tU/D = 100 to the end of the run (300), created before running ---
        int startStep = statsStartStep(sim, d, uRef); sim.println("setup_monitors: stats start step " + startStep);
        FieldFunction pFF = sim.getFieldFunctionManager().getFunction("Pressure");
        fieldMean(sim, "u_mean", uFF, region, startStep);
        fieldMean(sim, "v_mean", vFF, region, startStep);
        fieldMean(sim, "p_mean", pFF, region, startStep);
        fieldVariance(sim, "u_var", uFF, region, startStep);
        fieldVariance(sim, "v_var", vFF, region, startStep);
        fieldVariance(sim, "p_var", pFF, region, startStep);

        // --- Non-dimensional statistics, exact names read by the external adapter ---
        // U = rho = 1, cp reference pressure 0 and dynamic pressure 0.5*rho*U^2.
        String q = "(0.5*${rho}*${U}*${U})";
        statsFF(sim, "u_over_U_mean", "${" + statFunctionName(sim, "Mean of ", uFF) + "}/${U}");
        statsFF(sim, "u_over_U_rms",  rms(statFunctionName(sim, "Variance of ", uFF)) + "/${U}");
        statsFF(sim, "v_over_U_mean", "${" + statFunctionName(sim, "Mean of ", vFF) + "}/${U}");
        statsFF(sim, "v_over_U_rms",  rms(statFunctionName(sim, "Variance of ", vFF)) + "/${U}");
        statsFF(sim, "cp_mean", "(${" + statFunctionName(sim, "Mean of ", pFF) + "}-0.0)/" + q);
        statsFF(sim, "cp_rms",  rms(statFunctionName(sim, "Variance of ", pFF)) + "/" + q);

        sim.saveState(sim.getSessionPath());
    }

    private static final double P1_XD = 2.0, P1_YD = 0.0;
    private static final double P2_XD = 5.0, P2_YD = 0.5;
    private static final double STATS_START_TUD = 100.0;

    private double parameter(Simulation sim, String name) {
        GlobalParameterManager paramMgr = sim.get(GlobalParameterManager.class);
        if (!paramMgr.has(name)) {
            throw new RuntimeException("setup_monitors: missing global parameter '" + name + "'.");
        }
        return ((ScalarGlobalParameter) paramMgr.getObject(name)).getQuantity().getSIValue();
    }

    // First time step of the statistics window: tU/D = 100, i.e. t = 100*D/U over the solver time step.
    private int statsStartStep(Simulation sim, double d, double uRef) {
        double dt = 0.02 * d / uRef; sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class).getTimeStep().setValue(dt); // spec: dt*U/D = 0.02, set here so the stats start step is right
        String envStart = System.getenv("T8_STATS_START"); double startTud = envStart != null ? Double.parseDouble(envStart) : STATS_START_TUD; double steps = startTud * d / uRef / dt;
        int start = (int) Math.round(steps);
        if (Math.abs(steps - start) > 1.0e-6) {
            sim.println("setup_monitors: WARNING tU/D = 100 is not a whole number of steps; starting at step " + start + ".");
        }
        return start;
    }

    private void timeReport(Simulation sim, String name) {
        ReportManager reportMgr = sim.getReportManager();
        ExpressionReport report;
        if (reportMgr.has(name)) {
            report = (ExpressionReport) reportMgr.getReport(name);
        } else {
            report = reportMgr.createReport(ExpressionReport.class);
            report.setPresentationName(name);
        }
        report.setDefinition("$Time*${U}/${D}");
        timestepMonitor(sim, name, report);
    }

    private void fieldMean(Simulation sim, String name, FieldFunction ff, Region region, int startStep) {
        MonitorManager monitorMgr = sim.getMonitorManager();
        FieldMeanMonitor monitor;
        if (monitorMgr.has(name)) {
            monitor = (FieldMeanMonitor) monitorMgr.getMonitor(name);
        } else {
            monitor = monitorMgr.createMonitor(FieldMeanMonitor.class);
            monitor.setPresentationName(name);
        }
        monitor.getParts().setObjects(region);
        monitor.setFieldFunction(ff);
        startAt(monitor.getStarUpdate(), startStep);
    }

    private void fieldVariance(Simulation sim, String name, FieldFunction ff, Region region, int startStep) {
        MonitorManager monitorMgr = sim.getMonitorManager();
        FieldVarianceMonitor monitor;
        if (monitorMgr.has(name)) {
            monitor = (FieldVarianceMonitor) monitorMgr.getMonitor(name);
        } else {
            monitor = monitorMgr.createMonitor(FieldVarianceMonitor.class);
            monitor.setPresentationName(name);
        }
        monitor.getParts().setObjects(region);
        monitor.setFieldFunction(ff);
        startAt(monitor.getStarUpdate(), startStep);
    }

    // Every time step, starting at startStep.
    private void startAt(StarUpdate update, int startStep) {
        update.getUpdateModeOption().setSelected(StarUpdateModeOption.Type.TIMESTEP);
        TimeStepUpdateFrequency frequency = update.getTimeStepUpdateFrequency();
        frequency.setTimeSteps(1);
        frequency.setStart(startStep);
    }

    // Function name of the field function a mean or variance monitor generates (same presentation name).
    private String statFunctionName(Simulation sim, String prefix, FieldFunction src) {
        for (FieldFunction ff : sim.getFieldFunctionManager().getObjects()) {
            if (!(ff instanceof UserFieldFunction) && ff.getPresentationName().equals(prefix + src.getPresentationName())) {
                return ff.getFunctionName();
            }
        }
        throw new RuntimeException("setup_monitors: no field function generated by '" + prefix + src.getPresentationName() + "'.");
    }

    // RMS of the fluctuation = sqrt(variance); the guard keeps round-off below zero from giving NaN.
    private String rms(String varianceFunction) {
        String v = "${" + varianceFunction + "}";
        return "(" + v + " > 0 ? sqrt(" + v + ") : 0)";
    }

    private void statsFF(Simulation sim, String name, String definition) {
        FieldFunctionManager ffMgr = sim.getFieldFunctionManager();
        UserFieldFunction ff;
        if (ffMgr.has(name)) {
            ff = (UserFieldFunction) ffMgr.getFunction(name);
        } else {
            ff = ffMgr.createFieldFunction();
            ff.setFunctionName(name);
            ff.setPresentationName(name);
        }
        ff.getTypeOption().setSelected(FieldFunctionTypeOption.Type.SCALAR);
        ff.setDefinition(definition);
    }

    private void forceCoefficient(Simulation sim, String name, Boundary bnd, double dx, double dy,
                                  Units ud, Units uu, Units ua, Units ul) {
        ReportManager reportMgr = sim.getReportManager();
        ForceCoefficientReport report;
        if (reportMgr.has(name)) {
            report = (ForceCoefficientReport) reportMgr.getReport(name);
        } else {
            report = reportMgr.createReport(ForceCoefficientReport.class);
            report.setPresentationName(name);
        }
        report.getParts().setObjects(bnd);
        report.getReferenceDensity().setDefinitionAndUnits("${rho}", ud);
        report.getReferenceVelocity().setDefinitionAndUnits("${U}", uu);
        report.getReferenceArea().setDefinitionAndUnits("${D}*1", ua);
        report.getDirection().setComponentsAndUnits(dx, dy, 0.0, ul);
        timestepMonitor(sim, name, report);
    }

    private PointPart pointPart(Simulation sim, String name, Region region, double x, double y, Units ul) {
        PartManager partMgr = sim.getPartManager();
        PointPart point;
        if (partMgr.has(name)) {
            point = (PointPart) partMgr.getObject(name);
        } else {
            point = partMgr.createPointPart(new NeoObjectVector(new Object[] {region}),
                new DoubleVector(new double[] {x, y, 0.0}));
            point.setPresentationName(name);
        }
        point.getInputParts().setObjects(region);
        point.getPointCoordinate().setCoordinate(ul, ul, ul, new DoubleVector(new double[] {x, y, 0.0}));
        return point;
    }

    private void pointProbe(Simulation sim, String name, FieldFunction ff, PointPart point) {
        ReportManager reportMgr = sim.getReportManager();
        MaxReport report;
        if (reportMgr.has(name)) {
            report = (MaxReport) reportMgr.getReport(name);
        } else {
            report = reportMgr.createReport(MaxReport.class);
            report.setPresentationName(name);
        }
        report.setFieldFunction(ff);
        report.getParts().setObjects(point);
        timestepMonitor(sim, name, report);
    }

    // Monitor with the same name as its report, updated every time step.
    private void timestepMonitor(Simulation sim, String name, Report report) {
        MonitorManager monitorMgr = sim.getMonitorManager();
        ReportMonitor monitor;
        if (monitorMgr.has(name)) {
            monitor = (ReportMonitor) monitorMgr.getMonitor(name);
        } else {
            monitor = (ReportMonitor) report.createMonitor();
            monitor.setPresentationName(name);
        }
        monitor.getStarUpdate().getUpdateModeOption().setSelected(StarUpdateModeOption.Type.TIMESTEP);
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
