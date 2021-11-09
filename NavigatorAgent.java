import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class NavigatorAgent extends Agent {
    private static final String SERVICE_DESCRIPTION = "NAVIGATOR_AGENT";
    
    public static final String GOLD = "gold";
    public static final String BREEZE = "breeze";
    public static final String PIT = "pit";
    public static final String STENCH = "stench";
    public static final String WAMPUS = "wampus";
    public static final String SCREAM = "scream";

    public static int UNKNOWN = -1;
    public static int YES = 0;
    public static int NO = 1;
    public static int MAYBE = 2;

    private Position pos;
    IntendedEnvironment env;
    Boolean gold_taken = false;
    Boolean wampus_killed = false;
    Hashtable<Position, Boolean> visitedCells;
    Hashtable<Position, Integer> safeNeighbors;
    Hashtable<Position, ArrayList<Position>> visitedEdges;

    @Override
    protected void setup() {
        pos = new Position();
        env = new IntendedEnvironment();
        visitedCells = new Hashtable<>();
        visitedEdges = new Hashtable<>();
        safeNeighbors = new Hashtable<>();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SpeleologistAgent.NAVIGATOR_AGENT_TYPE);
        sd.setName(SERVICE_DESCRIPTION);
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new SolutionMakingPerformer());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("[Navigator]: Agent "+getAID().getName()+" terminating");
    }

    private class SolutionMakingPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                if (gold_taken) {
                    System.out.println("[Navigator]: Game is finished");
                    System.exit(0);
                }

                String stateStr = msg.getContent();
                stateStr = stateStr.substring(1, stateStr.length()-1);
                String[] cell_states = stateStr.split(", ");

                if (stateStr.equals("scream")) {
                    wampus_killed = true;
                }

                System.out.println("\n\n");
                System.out.println("[Navigator]: Current cell state — " + Arrays.toString(cell_states));
                System.out.println("[Navigator]: Current agent position — (" + pos.getY() + ", " + pos.getX() + ")");

                String[] commands = makeSolution(cell_states);

                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(Arrays.toString(commands));
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }

    private String[] makeSolution(String[] cellStates) {
        int[] commandsInt;

        IntendedCell currentCell = env.getGrid().get(pos);

        for (String state: cellStates){
            currentCell.processState(state);
        }
        currentCell.updateFeelings();

        boolean wampus_near = updateNeighbors(pos);

        if (currentCell.getGold() == NavigatorAgent.YES) {
            gold_taken = true;
            commandsInt = new int[] {SpeleologistAgent.TAKE};
        } else if (wampus_near && !wampus_killed) {
            commandsInt = new int[0];
            Position[] neighborsPos = getNeighborsPosition();
            for (Position position: neighborsPos){
                if (!position.isInside())
                    continue;

                if (env.getGrid().get(position).getWampus() == NavigatorAgent.YES) {
                    commandsInt = generateCommand(position, SpeleologistAgent.SHOOT);
                    break;
                }
            }
        } else {
            Position[] safeNeighbors = getSafeNeighbors();
            if (safeNeighbors.length == 0) {
                System.out.println("[Navigator]: No chance to survive");
                System.exit(0);
            }

            Position safeNeighbor = safeNeighbors[0];

            commandsInt = generateCommand(safeNeighbor, SpeleologistAgent.MOVE);
        }

        String[] commands = new String[commandsInt.length];
        for (int i = 0; i < commandsInt.length; ++i){
            commands[i] = SpeleologistAgent.actionCodes.get(commandsInt[i]);
        }

        System.out.println("[Navigator]: " + Arrays.toString(commands));

        return commands;
    }

    private int[] generateCommand(Position targetPos, int action) {
        int direction;
        if (pos.getY() < targetPos.getY()) {
            direction = SpeleologistAgent.UP;
        } else if (pos.getY() > targetPos.getY()) {
            direction = SpeleologistAgent.DOWN;
        } else if (pos.getX() < targetPos.getX()) {
            direction = SpeleologistAgent.RIGHT;
        } else {
            direction = SpeleologistAgent.LEFT;
        }

        if (action == SpeleologistAgent.MOVE) {
            Position temp = new Position(pos.getX(), pos.getY());
            visitedEdges.computeIfAbsent(temp, k -> new ArrayList<>());
            visitedEdges.get(temp).add(targetPos);

            if (visitedEdges.get(temp).size() == safeNeighbors.get(temp)) {
                visitedCells.put(temp, true);
            }

            pos.setY(targetPos.getY());
            pos.setX(targetPos.getX());
        }

        return new int[] {direction, action};
    }

    private Position[] getSafeNeighbors() {
        Position[] neighborsPos = getNeighborsPosition();
        ArrayList<Position> safePositions = new ArrayList<>();

        for (Position position: neighborsPos){
            if (!position.isInside() || visitedCells.get(position) != null ||
                    (visitedEdges.get(pos) != null && visitedEdges.get(pos).contains(position)) )
                continue;

            if ((env.getGrid().get(position).getSafe() == NavigatorAgent.YES))
                safePositions.add(position);
        }

        safeNeighbors.computeIfAbsent(pos, k -> safePositions.size());

        return safePositions.toArray(new Position[0]);
    }

    private IntendedCell getIntendedCell(Position pos) {
        IntendedCell cell = env.getGrid().get(pos);
        if (cell == null && pos.isInside()){
            cell = new IntendedCell();
            env.getGrid().put(pos, cell);
        }
        return cell;
    }

    private ArrayList<IntendedCell> getNeighborsIntendedCells() {
        ArrayList<IntendedCell> cells = new ArrayList<>();
        int[][] deltas = new int[][] { {0, 1}, {0, -1}, {1, 0}, {-1, 0} };

        for (int[] step : deltas) {
            IntendedCell cell = getIntendedCell(new Position(pos.getX() + step[0],pos.getY() + step[1]));
            if (cell != null)
                cells.add(cell);
        }
        return cells;
    }

    private Position[] getNeighborsPosition(){
        Position upNeighbor = new Position(pos.getX(), pos.getY() + 1);
        Position rightNeighbor = new Position(pos.getX() + 1, pos.getY());
        Position bottomNeighbor = new Position(pos.getX(), pos.getY() - 1);
        Position leftNeighbor = new Position(pos.getX() - 1, pos.getY());
        return new Position[]{ rightNeighbor, upNeighbor, leftNeighbor, bottomNeighbor };
    }

    private boolean updateNeighbors(Position agentPos) {
        IntendedCell currentCell = env.getGrid().get(agentPos);
        ArrayList<IntendedCell> neighborCells = getNeighborsIntendedCells();

        int no_wampus = 0;
        for (IntendedCell cell: neighborCells) {
            if (cell.getSafe() == NavigatorAgent.YES || cell.getWampus() == NavigatorAgent.NO)
                no_wampus++;
            if (cell.getSafe() == NavigatorAgent.YES)
                continue;

            if (currentCell.getSafe() == NavigatorAgent.YES && currentCell.getBreeze() != NavigatorAgent.YES && currentCell.getStench() != NavigatorAgent.YES) {
                cell.setSafe(NavigatorAgent.YES);
            }

            if (currentCell.getBreeze() == NavigatorAgent.NO && currentCell.getStench() == NavigatorAgent.NO) {
                cell.setSafe(NavigatorAgent.YES);
            }

            if (currentCell.getBreeze() == NavigatorAgent.NO) {
                cell.setPit(NavigatorAgent.NO);
            }

            if (currentCell.getStench() == NavigatorAgent.NO) {
                cell.setWampus(NavigatorAgent.NO);
            }

            if (currentCell.getStench() == NavigatorAgent.YES) {
                if (cell.getWampus() == NavigatorAgent.UNKNOWN){
                    cell.setSafe(NavigatorAgent.MAYBE);
                    cell.setWampus(NavigatorAgent.MAYBE);
                }
            }

            if (currentCell.getBreeze() == NavigatorAgent.YES){
                if (cell.getPit() == NavigatorAgent.UNKNOWN){
                    cell.setSafe(NavigatorAgent.MAYBE);
                    cell.setPit(NavigatorAgent.MAYBE);
                }
            }

            cell.updateSafety();
        }

        if (currentCell.getStench() == NavigatorAgent.YES && no_wampus + 1 == neighborCells.size()) {
            for (IntendedCell room: neighborCells) {
                if ( room.getSafe() != NavigatorAgent.YES && room.getWampus() != NO) {
                    room.setWampus( NavigatorAgent.YES);
                }
            }
            return true;
        }

        return false;
    }
}

class IntendedEnvironment {

    private final Hashtable<Position, IntendedCell> grid;

    IntendedEnvironment(){
        grid = new Hashtable<>();

        IntendedCell start = new IntendedCell();
        start.setSafe(NavigatorAgent.YES);

        grid.put( new Position(0, 0), start);
    }

    public Hashtable<Position, IntendedCell> getGrid() {
        return grid;
    }
}

class IntendedCell {
    private int safe;
    private int gold;
    private int breeze;
    private int pit;
    private int stench;
    private int wampus;

    public IntendedCell() {
        this.stench = NavigatorAgent.UNKNOWN;
        this.breeze = NavigatorAgent.UNKNOWN;
        this.pit = NavigatorAgent.UNKNOWN;
        this.wampus = NavigatorAgent.UNKNOWN;
        this.safe = NavigatorAgent.UNKNOWN;
        this.gold = NavigatorAgent.UNKNOWN;
    }

    public void processState(String stateName){
        switch (stateName){
            case NavigatorAgent.SCREAM:
                break;
            case NavigatorAgent.WAMPUS:
                this.setWampus(NavigatorAgent.YES);
                break;
            case NavigatorAgent.PIT:
                this.setPit(NavigatorAgent.YES);
                break;
            case NavigatorAgent.BREEZE:
                this.setBreeze(NavigatorAgent.YES);
                break;
            case NavigatorAgent.STENCH:
                this.setStench(NavigatorAgent.YES);
                break;
            case NavigatorAgent.GOLD:
                this.setGold(NavigatorAgent.YES);
                break;
        }
    }

    public void updateFeelings() {
        if (stench == NavigatorAgent.UNKNOWN) {
            stench = NavigatorAgent.NO;
        }
        if (breeze == NavigatorAgent.UNKNOWN) {
            breeze = NavigatorAgent.NO;
        }
    }

    public void updateSafety() {
        if (wampus == NavigatorAgent.NO && pit == NavigatorAgent.NO)
            safe = NavigatorAgent.YES;
    }

    public int getStench() {
        return stench;
    }

    public void setStench(int stench) {
        this.stench = stench;
    }

    public int getBreeze() {
        return breeze;
    }

    public void setBreeze(int breeze) {
        this.breeze = breeze;
    }

    public int getPit() {
        return pit;
    }

    public void setPit(int pit) {
        this.pit = pit;
    }

    public int getWampus() {
        return wampus;
    }

    public void setWampus(int wampus) {
        this.wampus = wampus;
    }

    public int getSafe() {
        return safe;
    }

    public void setSafe(int safe) {
        this.safe = safe;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public int getGold() {
        return gold;
    }

}
class Position {
    private int x;
    private int y;

    Position(){
        this.x = 0;
        this.y = 0;
    }

    Position(int x, int y){
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        Position position = (Position)obj;
        return this.x == position.getX() && this.y == position.getY();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    public boolean isInside() { return x >= 0 && x < 4 && y >= 0 && y < 4; }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}