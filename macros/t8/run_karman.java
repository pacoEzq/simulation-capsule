// run_karman.java
// Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1, mu = 0.01).
// Runs the solver with dt*U/D = 0.02, 10 inner iterations per time step,
// up to physical time t*U/D read from env var T8_TEND (default 300); saves at the end.

import star.common.*;

public class run_karman extends StarMacro {

    public void execute() {

        Simulation sim = getActiveSimulation();
        UnitsManager units = sim.getUnitsManager();
        Units us = units.getUnits("s");

        // --- Time step size: dt*U/D = 0.02 (non-dimensional) ---
        ImplicitUnsteadySolver timeSolver =
            (ImplicitUnsteadySolver) sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class);
        timeSolver.getTimeStep().setValueAndUnits(0.02, us);

        // --- Inner iterations per time step ---
        InnerIterationStoppingCriterion innerCrit = (InnerIterationStoppingCriterion)
            sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Inner Iterations");
        innerCrit.setMaximumNumberInnerIterations(10);

        // --- Physical time stop: t*U/D from env var T8_TEND (default 300) ---
        double tEnd = 300.0;
        String tEndEnv = System.getenv("T8_TEND");
        if (tEndEnv != null) {
            tEnd = Double.parseDouble(tEndEnv);
        }
        PhysicalTimeStoppingCriterion timeCrit = (PhysicalTimeStoppingCriterion)
            sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time");
        timeCrit.getMaximumTime().setValue(tEnd);
        // Default 'Maximum Steps' (1000) would stop the run at tU/D = 20; physical time governs.
        if (sim.getSolverStoppingCriterionManager().has("Maximum Steps")) { ((StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps")).setIsUsed(false); }
        timeCrit.setIsUsed(true);

        sim.getSimulationIterator().run();

        // --- Save ---
        sim.saveState(sim.getSessionPath());
    }
}
