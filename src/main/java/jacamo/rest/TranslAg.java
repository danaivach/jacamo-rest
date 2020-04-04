package jacamo.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import cartago.ArtifactId;
import cartago.ArtifactInfo;
import cartago.CartagoException;
import cartago.CartagoService;
import cartago.WorkspaceId;
import jaca.CAgentArch;
import jason.JasonException;
import jason.architecture.AgArch;
import jason.asSemantics.Agent;
import jason.asSemantics.CircumstanceListener;
import jason.asSemantics.IntendedMeans;
import jason.asSemantics.Intention;
import jason.asSemantics.Option;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.Plan;
import jason.asSyntax.PlanBody;
import jason.asSyntax.PlanLibrary;
import jason.asSyntax.Trigger;
import jason.asSyntax.Trigger.TEType;
import jason.asSyntax.parser.ParseException;
import jason.infra.centralised.BaseCentralisedMAS;
import jason.infra.centralised.CentralisedAgArch;
import jason.stdlib.print;
import ora4mas.nopl.GroupBoard;
import ora4mas.nopl.SchemeBoard;
import ora4mas.nopl.oe.Group;

public class TranslAg {

    Map<String, StringBuilder> agLog = new HashMap<>();
    Executor executor = Executors.newFixedThreadPool(4);

    /**
     * Get list of existing agents Example: ["ag1","ag2"]
     * 
     * @return Set of agents;
     */
    public Set<String> getAgents() {
        return BaseCentralisedMAS.getRunner().getAgs().keySet();
    }

    /**
     * Create agent and corresponfing asl file with the agName if possible, or agName_1, agName_2,...
     * 
     * @param agName
     * @return
     * @throws Exception
     * @throws JasonException
     * @throws FileNotFoundException
     */
    public String createAgent(String agName) throws Exception, JasonException, FileNotFoundException {
        String givenName = BaseCentralisedMAS.getRunner().getRuntimeServices().createAgent(agName, null, null, null,
                null, null, null);
        BaseCentralisedMAS.getRunner().getRuntimeServices().startAgent(givenName);
        // set some source for the agent
        Agent ag = getAgent(givenName);

        try {

            File f = new File("src/agt/" + givenName + ".asl");
            if (!f.exists()) {
                f.createNewFile();
                FileOutputStream outputFile = new FileOutputStream(f, false);
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("//Agent created automatically\n\n");
                stringBuilder.append("!start.\n\n");
                stringBuilder.append("+!start <- .print(\"Hi\").\n\n");
                stringBuilder.append("{ include(\"$jacamoJar/templates/common-cartago.asl\") }\n");
                stringBuilder.append("{ include(\"$jacamoJar/templates/common-moise.asl\") }\n");
                stringBuilder.append(
                        "// uncomment the include below to have an agent compliant with its organisation\n");
                stringBuilder.append("//{ include(\"$moiseJar/asl/org-obedient.asl\") }");
                byte[] bytes = stringBuilder.toString().getBytes();
                outputFile.write(bytes);
                outputFile.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        ag.load(new FileInputStream("src/agt/" + givenName + ".asl"), givenName + ".asl");
        // ag.setASLSrc("no-inicial.asl");
        createAgLog(givenName, ag);
        return givenName;
    }
    
    /**
     * Creates a log area for an agent
     * 
     * @param agName agent name
     * @param ag     agent object
     */
    protected void createAgLog(String agName, Agent ag) {
        // adds a log for the agent
        if (agLog.get(agName) == null) {
            agLog.put(agName, new StringBuilder());
            ag.getTS().getLogger().addHandler(new StreamHandler() {
                @Override
                public void publish(LogRecord l) {
                    addAgLog(agName, l.getMessage());
                }
            });
        }
    }
    
    /**
     * Add a message to the agent log.
     * 
     * @param agName agent name
     * @param msg    message to be added
     */
    protected void addAgLog(String agName, String msg) {
        StringBuilder o = agLog.get(agName);
        if (o == null) {
            o = new StringBuilder();
            agLog.put(agName, o);
        } else {
            o.append("\n");
        }
        String dt = new SimpleDateFormat("dd-MM-yy HH:mm:ss").format(new Date());
        o.append("[" + dt + "] " + msg);
    }
    
    /**
     * Get agent information (namespaces, roles, missions and workspaces)
     * 
     * @param agName name of the agent
     * @return A Map with agent information
     * @throws CartagoException
     * 
     */
    public Map<String, Object> getAgentDetails(String agName) throws CartagoException {

        Agent ag = getAgent(agName);

        // get workspaces the agent are in (including organisations)
        List<String> workspacesIn = new ArrayList<>();
        CAgentArch cartagoAgArch = getCartagoArch(ag);

        for (WorkspaceId wid : cartagoAgArch.getSession().getJoinedWorkspaces()) {
            workspacesIn.add(wid.getName());
        }
        List<String> nameSpaces = new ArrayList<>();
        ag.getBB().getNameSpaces().forEach(x -> {
            nameSpaces.add(x.toString());
        });

        // get groups and roles this agent plays
        List<Object> roles = new ArrayList<>();
        for (GroupBoard gb : GroupBoard.getGroupBoards()) {
            if (workspacesIn.contains(gb.getOEId())) {
                gb.getGrpState().getPlayers().forEach(p -> {
                    if (p.getAg().equals(agName)) {
                        Map<String, Object> groupRole = new HashMap<>();
                        groupRole.put("group", gb.getArtId());
                        groupRole.put("role", p.getTarget());
                        roles.add(groupRole);
                    }
                });

            }
        }

        // get schemed this agent belongs
        List<Object> missions = new ArrayList<>();
        for (SchemeBoard schb : SchemeBoard.getSchemeBoards()) {
            schb.getSchState().getPlayers().forEach(p -> {
                if (p.getAg().equals(agName)) {
                    Map<String, Object> schemeMission = new HashMap<>();
                    schemeMission.put("scheme", schb.getArtId());
                    schemeMission.put("mission", p.getTarget());
                    List<Object> responsibles = new ArrayList<>();
                    schemeMission.put("responsibles", responsibles);
                    for (Group gb : schb.getSchState().getGroupsResponsibleFor()) {
                        responsibles.add(gb.getId());
                    }
                    missions.add(schemeMission);
                }
            });
        }

        List<Object> workspaces = new ArrayList<>();
        workspacesIn.forEach(wksName -> {
            Map<String, Object> workspace = new HashMap<>();
            workspace.put("workspace", wksName);
            List<Object> artifacts = new ArrayList<>();
            try {
                for (ArtifactId aid : CartagoService.getController(wksName).getCurrentArtifacts()) {
                    ArtifactInfo info = CartagoService.getController(wksName).getArtifactInfo(aid.getName());
                    info.getObservers().forEach(y -> {
                        if (y.getAgentId().getAgentName().equals(agName)) {
                            // Build returning object
                            Map<String, Object> artifact = new HashMap<String, Object>();
                            artifact.put("artifact", info.getId().getName());
                            artifact.put("type", info.getId().getArtifactType());
                            artifacts.add(artifact);
                        }
                    });
                }
                workspace.put("artifacts", artifacts);
                workspaces.add(workspace);
            } catch (CartagoException e) {
                e.printStackTrace();
            }
        });

        Map<String, Object> agent = new HashMap<>();
        agent.put("agent", agName);
        agent.put("namespaces", nameSpaces);
        agent.put("roles", roles);
        agent.put("missions", missions);
        agent.put("workspaces", workspaces);

        return agent;
    }

    /**
     * Send a command to an agent
     * 
     * @param agName name of the agent
     * @param sCmd   command to be executed
     * @return Status message
     * @throws ParseException 
     */
    Unifier execCmd(Agent ag, PlanBody lCmd) throws ParseException {
        Trigger te = ASSyntax.parseTrigger("+!run_repl_expr");
        Intention i = new Intention();
        IntendedMeans im = new IntendedMeans(new Option(new Plan(null, te, null, lCmd), new Unifier()), te);
        i.push(im);

        Lock lock = new ReentrantLock();
        Condition goalFinished  = lock.newCondition();
        executor.execute( () -> {
                /*GoalListener gl = new GoalListener() {                 
                    public void goalSuspended(Trigger goal, String reason) {}
                    public void goalStarted(Event goal) {}
                    public void goalResumed(Trigger goal) {}
                    public void goalFinished(Trigger goal, FinishStates result) {
                        System.out.println("finished!");
                        if (goal.equals(te)) {
                            // finished
                            //if (result.equals(FinishStates.achieved)) {
                            //}                           
                            try {
                                lock.lock();
                                goalFinished.signalAll();
                            } finally {
                                lock.unlock();
                            }
                        }                       
                    }
                    public void goalFailed(Trigger goal) {
                        if (goal.equals(te)) {
                            try {
                                lock.lock();
                                goalFinished.signalAll();
                            } finally {
                                lock.unlock();
                            }
                        }                                                   
                    }
                };*/
                CircumstanceListener cl = new CircumstanceListener() {
                    public void intentionDropped(Intention ci) {
                        System.out.println("*finished!"+ci);
                        if (ci.equals(i)) {
                            try {
                                lock.lock();
                                goalFinished.signalAll();
                            } finally {
                                lock.unlock();
                            }
                        }
                    };

                };
                TransitionSystem ts = ag.getTS();
                try {
                    lock.lock();
                    //ts.addGoalListener(gl);
                    ts.getC().addEventListener(cl);
                    ts.getC().addRunningIntention(i);
                    ts.getUserAgArch().wake();
                    goalFinished.await();
                    //ts.removeGoalListener(gl);
                    ts.getC().removeEventListener(cl);
                } catch (InterruptedException e) {                          
                } finally {
                    lock.unlock();
                }
                System.out.println("fim thread");
        });
        try {
            lock.lock();
            goalFinished.await();
            
            return im.getUnif();
        } catch (InterruptedException e) {
        } finally {
            lock.unlock();
        }
        return null;
    }
    
    /**
     * Return agent object by agent's name
     * 
     * @param agName name of the agent
     * @return Agent object
     */
    public Agent getAgent(String agName) {
        CentralisedAgArch cag = BaseCentralisedMAS.getRunner().getAg(agName);
        if (cag != null)
            return cag.getTS().getAg();
        else
            return null;
    }

    /**
     * Get agent's CArtAgO architecture
     * 
     * @param ag Agent object
     * @return agent's CArtAgO architecture
     */
    protected CAgentArch getCartagoArch(Agent ag) {
        AgArch arch = ag.getTS().getUserAgArch().getFirstAgArch();
        while (arch != null) {
            if (arch instanceof CAgentArch) {
                return (CAgentArch) arch;
            }
            arch = arch.getNextAgArch();
        }
        return null;
    }
    
    public void getPlansSuggestions(String agName, Map<String, String> commands) {
        try {
            // get agent's plans
            Agent ag = getAgent(agName);
            if (ag != null) {
                PlanLibrary pl = ag.getPL();
                for (Plan plan : pl.getPlans()) {
                    
                    // do not add plans that comes from jar files (usually system's plans)
                    if (plan.getSource().startsWith("jar:file") || plan.getSource().equals("kqmlPlans.asl"))
                        continue;

                    // add namespace when it is not default
                    String ns = "";
                    if (!plan.getNS().equals(Literal.DefaultNS)) {
                        ns = plan.getNS().toString() + "::";
                    }

                    String terms = "";
                    if (plan.getTrigger().getLiteral().getArity() > 0) {
                        for (int i = 0; i < plan.getTrigger().getLiteral().getArity(); i++) {
                            if (i == 0)
                                terms = "(";
                            terms += plan.getTrigger().getLiteral().getTerm(i).toString();
                            if (i < plan.getTrigger().getLiteral().getArity() - 1)
                                terms += ", ";
                            else
                                terms += ")";
                        }
                    }

                    // when it is a goal or test goal, do not add operator
                    if ((plan.getTrigger().getType() == TEType.achieve)
                            || (plan.getTrigger().getType() == TEType.test)) {


                        commands.put(ns + plan.getTrigger().getType().toString()
                        + plan.getTrigger().getLiteral().getFunctor() + terms, "");

                    }
                    // when it is belief, do not add type which is anyway empty
                    else if (plan.getTrigger().getType() == TEType.belief) {
                        commands.put(ns + plan.getTrigger().getOperator().toString()
                                + plan.getTrigger().getLiteral().getFunctor() + terms, "");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getEASuggestions(String agName, Map<String, String> commands) throws CartagoException {
        try {
            Agent ag = getAgent(agName);
            // get external actions (from focused artifacts)
            CAgentArch cartagoAgArch = getCartagoArch(ag);
            for (WorkspaceId wid : cartagoAgArch.getSession().getJoinedWorkspaces()) {
                String wksName = wid.getName();
                for (ArtifactId aid : CartagoService.getController(wksName).getCurrentArtifacts()) {

                    // operations
                    ArtifactInfo info = CartagoService.getController(wksName).getArtifactInfo(aid.getName());

                    info.getObservers().forEach(y -> {
                        if (y.getAgentId().getAgentName().equals(agName)) {
                            info.getOperations().forEach(z -> {
                                String params = "";
                                for (int i = 0; i < z.getOp().getNumParameters(); i++) {
                                    if (i == 0) params = "(";
                                    params += "arg" + i;
                                    if (i == z.getOp().getNumParameters() - 1)
                                        params += ")";
                                    else
                                        params += ", ";
                                }

                                commands.put(z.getOp().getName() + params, "");
                            });
                        }
                    });

                }
            }
        } catch (CartagoException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get list of internal actions for an agent
     * 
     * @return List of internal actions
     */
    public void getIASuggestions(Map<String,String> cmds) {
        try {
            ClassPath classPath = ClassPath.from(print.class.getClassLoader());
            Set<ClassInfo> allClasses = classPath.getTopLevelClassesRecursive("jason.stdlib");

            allClasses.forEach(a -> {
                try {
                    Class<?> c = a.load();
                    if (c.isAnnotationPresent(jason.stdlib.Manual.class)) {
                        // add full predicate provided by @Manual
                        jason.stdlib.Manual annotation = (jason.stdlib.Manual) c
                                .getAnnotation(jason.stdlib.Manual.class);
                        cmds.put(annotation.literal(), annotation.hint().replaceAll("\"", "`").replaceAll("'", "`"));
					} else {
						// add just the functor of the internal action
						cmds.put("." + a.getSimpleName(), "");
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}