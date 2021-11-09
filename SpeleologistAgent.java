import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Objects;

public class SpeleologistAgent extends Agent {

    public static String ENVIRONMENT_TYPE = "ENVIRONMENT_AGENT";
    public static String NAVIGATOR_AGENT_TYPE = "NAVIGATOR_AGENT";
    public static String ENVIRONMENT_CONVERSATION_ID = "ENVIRONMENT_CONVERSATION";
    public static String NAVIGATOR_CONVERSATION_ID = "NAVIGATOR_CONVERSATION";
    public static String CONNECT = "CONNECT";

    public static int MOVE = 0;
    public static int SHOOT = 1;
    public static int TAKE = 2;
    public static int UP = 3;
    public static int RIGHT = 4;
    public static int DOWN = 5;
    public static int LEFT = 6;

    public static java.util.HashMap<Integer, String> actionCodes = new java.util.HashMap<Integer, String>() {{
        put(MOVE, "move");
        put(SHOOT, "shoot");
        put(TAKE, "take");
        put(UP, "up");
        put(RIGHT, "right");
        put(DOWN, "down");
        put(LEFT, "left");
    }};

    private AID environmentAgent;
    private AID navigationAgent;
    private String environmentState = "";

    @Override
    protected void setup() {
        addBehaviour(new EnvironmentFindPerformer());
    }

    private class EnvironmentFindPerformer extends Behaviour {
        private int step = 0;

        @Override
        public void action() {
            if (step == 0) {
                DFAgentDescription dfd = new DFAgentDescription();

                ServiceDescription sd = new ServiceDescription();
                sd.setType(ENVIRONMENT_TYPE);

                dfd.addServices(sd);

                try {
                    DFAgentDescription[] result = DFService.search(myAgent, dfd);
                    if (result.length > 0) {
                        environmentAgent = result[0].getName();
                        myAgent.addBehaviour(new EnvironmentConnector());
                        ++step;
                    } else {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (FIPAException e) {
                    e.printStackTrace();
                }
            }
        }
        @Override
        public boolean done() {
            return step == 1;
        }
    }

    private class EnvironmentConnector extends Behaviour {
        private MessageTemplate mt;
        private int step = 0;

        @Override
        public void action() {
            switch (step) {
                case 0:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(environmentAgent);
                    cfp.setContent(CONNECT);
                    cfp.setConversationId(ENVIRONMENT_CONVERSATION_ID);
                    cfp.setReplyWith("cfp"+System.currentTimeMillis());
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(ENVIRONMENT_CONVERSATION_ID),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.CONFIRM) {
                            environmentState = reply.getContent();
                            myAgent.addBehaviour(new NavigatorAgentPerformer());
                            step = 2;
                        }
                    } else { block(); }
                    break;
            }
        }
        @Override
        public boolean done() {
            return step == 2;
        }
    }

    private class NavigatorAgentPerformer extends Behaviour {
        private int step = 0;
        private MessageTemplate mt;

        @Override
        public void action() {
            switch (step) {
                case 0: {
                    DFAgentDescription dfd = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType(NAVIGATOR_AGENT_TYPE);
                    dfd.addServices(sd);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, dfd);
                        if (result.length > 0) {
                            navigationAgent = result[0].getName();
                            ++step;
                        } else {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                }
                case 1: {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(navigationAgent);
                    msg.setContent(environmentState);
                    msg.setConversationId(NAVIGATOR_CONVERSATION_ID);
                    msg.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(msg);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_CONVERSATION_ID),
                            MessageTemplate.MatchInReplyTo(msg.getReplyWith()));
                    step = 2;
                }
                case 2: {
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            String commandsStr = reply.getContent();
                            commandsStr = commandsStr.substring(1, commandsStr.length()-1);
                            String[] commands = commandsStr.split(", ");

                            if (commands.length == 1){
                                sendInstruction("take", TAKE);
                            } else if (commands.length == 2 && Objects.equals(commands[1], actionCodes.get(SHOOT))){
                                sendInstruction(commands[0], SHOOT);
                            } else if (commands.length == 2 && Objects.equals(commands[1], actionCodes.get(MOVE))){
                                sendInstruction(commands[0], MOVE);
                            } else {
                                System.out.println("[Speleologist]: can not parse navigator commands");
                            }
                            ++step;
                        }
                    } else { block(); }
                    break;

                }
                case 3:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        environmentState = reply.getContent();
                        step = 1;
                    }
                    else { block(); }
                    break;
            }
        }

        @Override
        public boolean done() {
            return step == 4;
        }

        private void sendInstruction(String instruction, int action) {
            ACLMessage msg = new ACLMessage(action);
            msg.addReceiver(environmentAgent);
            msg.setContent(instruction);
            msg.setConversationId(NAVIGATOR_CONVERSATION_ID);
            msg.setReplyWith("order"+System.currentTimeMillis());
            myAgent.send(msg);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_CONVERSATION_ID),
                    MessageTemplate.MatchInReplyTo(msg.getReplyWith()));
        }
    }
}
