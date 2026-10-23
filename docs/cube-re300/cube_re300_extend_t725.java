// cube_re300_extend_t725.java: raise Maximum Physical Time to 725 s, run, save under a new name.
package macro;

import star.common.*;

public class cube_re300_extend_t725 extends StarMacro {

  @Override
  public void execute() {
    Simulation simulation_0 = getActiveSimulation();
    Units units_0 = ((Units) simulation_0.getUnitsManager().getObject("s"));
    PhysicalTimeStoppingCriterion physicalTimeStoppingCriterion_0 =
      ((PhysicalTimeStoppingCriterion) simulation_0.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time"));
    physicalTimeStoppingCriterion_0.getMaximumTime().setValueAndUnits(725.0, units_0);
    simulation_0.getSimulationIterator().run();
    simulation_0.saveState("cube_re0300_unsteady_t725.sim");
  }
}
