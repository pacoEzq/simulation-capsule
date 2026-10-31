// export_signals.java
// Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1, mu = 0.01), region fluid, 2D.
// Exports full histories of the monitors left by setup_monitors to work/signals_raw/, one CSV
// per magnitude: time column tU_D (monitor tU_D = t*U/D) and one column cd, cl, u_U or v_U.
// Headers carry no dimensional magnitudes; probe velocities are divided by the parameter U.
// Also writes cells.csv: header cells and the value of the cell_count report.

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import star.common.*;
import star.base.report.*;

public class export_signals extends StarMacro {

    public void execute() {

        Simulation sim = getActiveSimulation();
        double uRef = parameter(sim, "U");

        double[] t = values(sim, "tU_D", 1.0);

        File outDir = new File(sim.getSessionDir(), "work" + File.separator + "signals_raw");
        outDir.mkdirs();

        writeCsv(outDir, "cd.csv", t, "cd", values(sim, "cd", 1.0));
        writeCsv(outDir, "cl.csv", t, "cl", values(sim, "cl", 1.0));
        writeCsv(outDir, "p1_u.csv", t, "u_U", values(sim, "p1_u", uRef));
        writeCsv(outDir, "p1_v.csv", t, "v_U", values(sim, "p1_v", uRef));
        writeCsv(outDir, "p2_u.csv", t, "u_U", values(sim, "p2_u", uRef));
        writeCsv(outDir, "p2_v.csv", t, "v_U", values(sim, "p2_v", uRef));
        writeCells(outDir, cellCount(sim));
    }

    // Value of the cell_count report, rounded to an integer.
    private long cellCount(Simulation sim) {
        ReportManager repMgr = sim.getReportManager();
        if (!repMgr.has("cell_count")) {
            throw new RuntimeException("export_signals: missing report 'cell_count'.");
        }
        return Math.round(repMgr.getReport("cell_count").getReportMonitorValue());
    }

    private void writeCells(File outDir, long cells) {
        File outFile = new File(outDir, "cells.csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("cells");
            pw.println(Long.toString(cells));
        } catch (IOException e) {
            throw new RuntimeException("export_signals: failed to write " + outFile, e);
        }
    }

    private double parameter(Simulation sim, String name) {
        GlobalParameterManager paramMgr = sim.get(GlobalParameterManager.class);
        if (!paramMgr.has(name)) {
            throw new RuntimeException("export_signals: missing global parameter '" + name + "'.");
        }
        return ((ScalarGlobalParameter) paramMgr.getObject(name)).getQuantity().getSIValue();
    }

    // Full history of a monitor, divided by scale.
    private double[] values(Simulation sim, String name, double scale) {
        MonitorManager monMgr = sim.getMonitorManager();
        if (!monMgr.has(name)) {
            throw new RuntimeException("export_signals: missing monitor '" + name + "'.");
        }
        double[] y = ((PlotableMonitor) monMgr.getMonitor(name)).getAllYValues();
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            out[i] = y[i] / scale;
        }
        return out;
    }

    private void writeCsv(File outDir, String fileName, double[] t, String colName, double[] col) {
        if (col.length != t.length) {
            throw new RuntimeException("export_signals: " + fileName + " has " + col.length
                + " samples, tU_D has " + t.length + ".");
        }
        File outFile = new File(outDir, fileName);
        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
            pw.println("tU_D," + colName);
            for (int i = 0; i < t.length; i++) {
                pw.println(Double.toString(t[i]) + "," + Double.toString(col[i]));
            }
        } catch (IOException e) {
            throw new RuntimeException("export_signals: failed to write " + outFile, e);
        }
    }
}
