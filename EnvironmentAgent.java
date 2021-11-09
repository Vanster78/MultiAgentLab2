import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class EnvironmentAgent extends Agent {

    public static String SERVICE_DESCRIPTION = "ENVIRONMENT_AGENT";
    AID id = new AID("EnvironmentAgent", AID.ISLOCALNAME);

    private static int H = 4;
    private static int W = 4;

    private static int GOLD = 0;
    private static int BREEZE = 1;
    private static int PIT = 2;
    private static int STENCH = 3;
    private static int WAMPUS = 4;
    private static int SCREAM = 5;

    public static HashMap<Integer, String> states = new HashMap<Integer, String>() {{
        put(GOLD, NavigatorAgent.GOLD);
        put(BREEZE, NavigatorAgent.BREEZE);
        put(PIT, NavigatorAgent.PIT);
        put(STENCH, NavigatorAgent.STENCH);
        put(WAMPUS, NavigatorAgent.WAMPUS);
        put(SCREAM, NavigatorAgent.SCREAM);
    }};

    private Cell[][] grid;
    private HashMap<AID, Pos> speleologists;

    @Override
    protected void setup() {
        System.out.println("Environment agent " + getAID().getName() + " is ready.");

        speleologists = new HashMap<>();

        grid = new Cell[][]
            {       { new Cell()              , new Cell(BREEZE)              , new Cell(PIT)   , new Cell(BREEZE) },
                    { new Cell(STENCH)        , new Cell()                    , new Cell(BREEZE), new Cell()       },
                    { new Cell(WAMPUS, STENCH), new Cell(BREEZE, STENCH, GOLD), new Cell(PIT)   , new Cell(BREEZE) },
                    { new Cell(STENCH)        , new Cell()                    , new Cell(BREEZE), new Cell(PIT)    } };

        DFAgentDescription dfd = new DFAgentDescription();

        ServiceDescription sd = new ServiceDescription();
        sd.setType(SpeleologistAgent.ENVIRONMENT_TYPE);
        sd.setName(SERVICE_DESCRIPTION);

        dfd.setName(getAID());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new SpeleologistConnectPerformer());
        addBehaviour(new SpeleologistMovePerformer());
        addBehaviour(new SpeleologistShootPerformer());
        addBehaviour(new SpeleologistGoldPerformer());
    }

    private class SpeleologistConnectPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String message = msg.getContent();

                if (Objects.equals(message, SpeleologistAgent.CONNECT)){
                    AID speleologist = msg.getSender();
                    speleologists.put(speleologist, new Pos(0, 0));

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    reply.setContent(grid[0][0].states.toString());
                    myAgent.send(reply);
                }
            } else { block(); }
        }
    }

    private class SpeleologistMovePerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.MOVE);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                String message = msg.getContent();
                AID speleologist = msg.getSender();
                Pos speleologistPos = speleologists.get(speleologist);

                int i = speleologistPos.i;
                int j = speleologistPos.j;

                System.out.println("[Environment]: Agent is at (" + i + ", " + j + ")");

                if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.DOWN))){
                    --i;
                } else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.UP))){
                    ++i;
                } else if(message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LEFT))){
                    --j;
                } else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.RIGHT))){
                    ++j;
                }

                speleologistPos.j = j;
                speleologistPos.i = i;

                System.out.println("[Environment]: Agent moves at (" + i + ", " + j + ")");

                ACLMessage reply = msg.createReply();
                reply.setPerformative(SpeleologistAgent.MOVE);
                reply.setContent(grid[i][j].states.toString());
                myAgent.send(reply);
            } else { block(); }
        }
    }

    private class SpeleologistShootPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.SHOOT);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {

                String message = msg.getContent();
                AID speleologist = msg.getSender();
                Pos speleologistPos = speleologists.get(speleologist);

                int y = speleologistPos.i;
                int x = speleologistPos.j;

                System.out.println("[Environment]: Agent is at (" + y + ", " + x + ")");

                boolean scream = false;
                if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.DOWN))){
                    for (int i = y - 1; i >= 0; --i) {
                        System.out.println("[Environment]: Arrow is at (" + i + ", " + x + ")");

                        if (grid[i][x].states.contains(EnvironmentAgent.states.get(WAMPUS))){
                            scream = true;
                            break;
                        }
                    }
                } else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.UP))){
                    for (int i = y + 1; i < H; ++i) {
                        System.out.println("[Environment]: Arrow is at (" + i + ", " + x + ")");

                        if (grid[i][x].states.contains(EnvironmentAgent.states.get(WAMPUS))){
                            scream = true;
                            break;
                        }
                    }
                } else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.LEFT))){
                    for (int i = x - 1; i >= 0; --i) {
                        System.out.println("[Environment]: Arrow is at (" + y + ", " + i + ")");
                        if (grid[y][i].states.contains(EnvironmentAgent.states.get(WAMPUS))){
                            scream = true;
                            break;
                        }
                    }
                } else if (message.equals(SpeleologistAgent.actionCodes.get(SpeleologistAgent.RIGHT))){
                    for (int i = x + 1; i < W; ++i) {
                        System.out.println("[Environment]: Arrow is at (" + y + ", " + i + ")");
                        if (grid[y][i].states.contains(EnvironmentAgent.states.get(WAMPUS))){
                            scream = true;
                            break;
                        }
                    }
                }

                ACLMessage reply = msg.createReply();
                reply.setPerformative(SpeleologistAgent.SHOOT);
                if (scream) {
                    System.out.println("[Environment]: Scream! Wampus is killed");
                    reply.setContent("[" + NavigatorAgent.SCREAM + "]");
                } else {
                    reply.setContent("[]");
                }
                myAgent.send(reply);
            }
            else { block(); }
        }
    }

    private class SpeleologistGoldPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(SpeleologistAgent.TAKE);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                AID speleologist = msg.getSender();
                Pos speleologistPos = speleologists.get(speleologist);

                int i = speleologistPos.i;
                int j = speleologistPos.j;

                if (grid[i][j].states.contains(EnvironmentAgent.states.get(GOLD))){
                    System.out.println("[Environment]: Gold is taken");
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(SpeleologistAgent.TAKE);
                    reply.setContent(grid[i][j].states.toString());
                    myAgent.send(reply);
                }
            } else { block(); }
        }
    }
}

class Cell {
    ArrayList<String> states = new ArrayList<>();
    Cell(int... args){
        for (int i: args){
            states.add(EnvironmentAgent.states.get(i));
        }
    }
}

class Pos {
    int i;
    int j;
    Pos(int row, int column) {
        this.i = row;
        this.j = column;
    }
}
