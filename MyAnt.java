package ants;

import ants.*;
import java.awt.Point;
import java.io.*;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

//
 // Future Improvements:
 // Support variable number of Scanners, with different preferred routes, for bigger maps.
 // Could optimize when scanners choose to return home.  Abstract out more logic.
 // Make threshold parameters easier to modify.
 // Leave the unused portion of the grid null, rather than default to a value.
//

/**
 * Implementation class for an ant.
 * Let's get these little buggers munching!
 * <p>
 * During communication, the entire {@link AntKnowledge} "knowledge" object of the sending ant is transfered to the receiver.
 * Any information updated more recently on the received grid will overwrite the contained grid.
 * <p>
 * When the program began running slowly, I inserted performance timers throughout the various methods.  This helped me gauge where the problem areas were.
 * By far, the biggest delays were in the "send" and "receive" methods.  Serializing the large GRID objects that each ant carried was a slow operation.
 * The options were to either 1. Send only the subset of spots that had been updated recently by this ant or 2. Send less frequently, or both.
 * The first would likely have been the most valuable, realizing the gain without losing information.  I chose the second because it was
 * quicker and didn't diminish the quality significantly.
 * <p>
 * The search algorithm in the Brain.localExplore method does a good job of searching the map.  Details can be found in the Javadoc of that method.
 * This method will rarely fail to traverse any valid squares.  However, it can happen.  An optimization could be made to handle this rare edge case.
 * <p>
 * Javadoc won't be created for inner classes, so detailed reference will have to be done by reading the comments in the source code.
 * <p>
 * When the scanning ant completes scanning the map, it becomes a worker ant on the spot.
 * <p>
 * @author Jesse Smith
 * @version  %I%, %G%
 * @since 1.0
 */
public class MyAnt implements ants.Ant {

    //My brain, which contains my knowledge and my logic center
    private AntBrain brain = null;
    
    //My name, randomly selected by the queen - This is just for fun during logging
    private String myName = null;
    
    final boolean DEBUG = false;
    
    //CONSTANTS
    //Any baby name options, this is just for fun.
    private String[] NAMES = {"MARY","PATRICIA","LINDA","BARBARA","ELIZABETH","JENNIFER","MARIA","SUSAN","MARGARET","DOROTHY","LISA","NANCY","KAREN","BETTY","HELEN","SANDRA","DONNA","CAROL","RUTH","SHARON","MICHELLE","LAURA","SARAH","KIMBERLY","DEBORAH","JESSICA","SHIRLEY","CYNTHIA","ANGELA","MELISSA","BRENDA","AMY","ANNA","REBECCA","VIRGINIA","KATHLEEN","PAMELA","MARTHA","DEBRA","AMANDA","STEPHANIE","CAROLYN","CHRISTINE","MARIE","JANET","CATHERINE","FRANCES","ANN","JOYCE","DIANE","JAMES","JOHN","ROBERT","MICHAEL","WILLIAM","DAVID","RICHARD","CHARLES","JOSEPH","THOMAS","CHRISTOPHER","DANIEL","PAUL","MARK","DONALD","GEORGE","KENNETH","STEVEN","EDWARD","BRIAN","RONALD","ANTHONY","KEVIN","JASON","MATTHEW","GARY","TIMOTHY","JOSE","LARRY","JEFFREY","FRANK","SCOTT","ERIC","STEPHEN","ANDREW","RAYMOND","GREGORY","JOSHUA","JERRY","DENNIS","WALTER","PATRICK","PETER","HAROLD","DOUGLAS","HENRY","CARL","ARTHUR","RYAN","ROGER"};
    
    /**
     * Returns an {@link Action} object identifying what this ant should do at this step.
     * <p>
     * If an Ant is brand new, they haven't been assigned a role yet, so they will move out 1 space and back to trigger communication.
     * <p>
     * Worker ants will wait until they know of food, then retrieve it.
     * <p>
     * Scanner ants will search for food, stopping back periodically to report their findings.
     * <p>
     * Traffic Cop ant will wait at home, and facilitate communication between scanners and workers.
     * 
     * @param surroundings the description of the ant's current surroundings
     * @return the {@link Action} to be taken by the ant, null if the surroundings input parameter isn't valid.
     */
    @Override
    public Action getAction(Surroundings surroundings) {
        AntLogger.setup(DEBUG);
        
        //Run this once per ant turn.
        dawn();

        //Used for logging
        if (DEBUG)
            if (this.brain.knowledge.year % 2 == 0 && this.brain.isTrafficCop()){
                PerfMonitor.log(this.brain.knowledge.year);
            }
        
        AntLogger.infoLog(this.toString());
        
        try {
            //Do things with surroundings object here
            PerfMonitor.startClock(PerfMonitor.METHOD_ANALYZE_SURROUNDINGS, this.hashCode());
            brain.analyzeSurroundings(surroundings);
            PerfMonitor.stopClock(PerfMonitor.METHOD_ANALYZE_SURROUNDINGS, this.hashCode());
        }
        catch (Exception e) {
            AntLogger.infoLog("Unexpected exception analyzing surroundings object: " + e.toString());
        }
        try {
            //If the ant is young, do some basic schooling.
            if (brain.inYouth())
                return (brain.dance());
            
            switch (this.brain.knowledge.role){
                case AntBrain.TRAFFIC_COP: 
                    //AntLogger.infoLog("------TRAFFIC COP------");
                    //this.brain.assignScanners();
                    return Action.HALT;
                case AntBrain.WORKER:
                    if (DEBUG) PerfMonitor.startClock(PerfMonitor.METHOD_DO_WORK, this.hashCode());
                    Action a = brain.doWork();
                    if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_DO_WORK, this.hashCode());
                    return a;
                case AntBrain.SCANNER:
                    //AntLogger.infoLog("------SCANNING LEFT------");
                    if (DEBUG) PerfMonitor.startClock(PerfMonitor.METHOD_SEARCH, this.hashCode());
                    Action b = brain.search();
                    if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_SEARCH, this.hashCode());
                    return b;
                default:
                    return(brain.doWork());
            }
        }
        catch (Exception e) {
            AntLogger.infoLog("Unexpected exception calculating next Action: " + e.toString());
        }
        
        AntLogger.infoLog("Couldn't calculate an action!");
        //This ant found an error and couldn't find a good action, so it will hang around.
        //This shouldn't ever happen, see error please.
        return Action.HALT;
        
    }

    /**
     * Calculates the optimal message to be communicated to a nearby ant.
     * <p>
     * This message is based on the information this ant has learned through it's travels, and by talking to other friendly ants.
     * To send a message, ants serialize their {@link AntKnowledge} "knowledge" object and send it to the other ant.
     * WORKER ants don't explore, and thus don't need to communicate their grid.
     * 
     * @return the ant's {@link AntKnowledge} object as a byte[], null if nothing to say to this ant.
     */
    @Override
    public byte[] send() {
        try {
            if (DEBUG) PerfMonitor.startClock(PerfMonitor.METHOD_SEND, this.hashCode());
        }
        catch (Exception e) {
            AntLogger.infoLog("Caught performance exception starting METHOD_RECEIVE check: " + e); 
        }
        //AntLogger.infoLog(myName + " is talking");
        byte[] message;
        
        try {
            AntKnowledge serialObject;
            if (myName == null){
                //I have nothing to say, I'm a new born.
                if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_SEND, this.hashCode());
                return null;
            }
            //If this ant is a WORKER ant, send a new knowledge object without a grid.
            if (this.brain.knowledge.role == AntBrain.WORKER && !this.brain.inYouth()){
                serialObject = new AntKnowledge(AntBrain.WORKER);
                serialObject.year = this.brain.knowledge.year;
                serialObject.age = this.brain.knowledge.age;
                serialObject.id = this.brain.knowledge.id;
            }
            else {
                serialObject = this.brain.knowledge;
            }
            
            //Serialize the knowledge object.
            if (DEBUG) PerfMonitor.startClock(PerfMonitor.SEND_BUNDLE, this.hashCode());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);   
            out.writeObject(serialObject);
            message = bos.toByteArray();
            out.close();
            bos.close();
            
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.SEND_BUNDLE, this.hashCode());
            if (this.brain.knowledge.role == AntBrain.SCANNER)
                this.brain.setNewFoodToReport(0);
        }
        catch (IOException e) {
            AntLogger.infoLog("Caught exception serializing grid object: " + e);
            return null;
        }
        catch (Exception e) {
            AntLogger.infoLog("Caught data exception serializing grid object: " + e);
            return null;
        }
        if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_SEND, this.hashCode());
        return (message);
        
    }

    /**
     * Receive the message from the nearby friendly ant.
     * <p>
     * Depending on what role I have, and the role of the other ant, different logic occurs.
     * <p>
     * @param data the message from the nearby ant.
     */
    @Override
    public void receive(byte[] data) {
        try {
            if (DEBUG) PerfMonitor.startClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
        }
        catch (Exception e) {
            AntLogger.infoLog("Caught performance exception starting METHOD_RECEIVE check: " + e); 
        }
        //AntLogger.infoLog(myName + " is listening");
        AntKnowledge friendKnowledge = null;
        if (myName == null){
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
            return;
        }
        if (data == null){
            //Nothing from other ant.
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
            return;
        }
        
        try {
            //Deserialize the data from the other ant.
            if (DEBUG) PerfMonitor.startClock(PerfMonitor.RECEIVE_BUNDLE, this.hashCode());
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInput in = new ObjectInputStream(bis);
            friendKnowledge = (AntKnowledge)in.readObject(); 
            bis.close();
            in.close();
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.RECEIVE_BUNDLE, this.hashCode());
        }
        catch (IOException e) {
           AntLogger.infoLog("Caught exception deserializing grid object: " + e); 
        }
        catch (ClassNotFoundException e) {
           AntLogger.infoLog("Couldn't cast received data as AntGrid: " + e); 
        }
        catch (PerformanceException e) {
           AntLogger.infoLog("Performance Check error when receiving data as AntGrid: " + e); 
        }
        catch (Exception e) {
           AntLogger.infoLog("Unknown error when receiving data as AntGrid: " + e); 
        }
        try {
            //If I am the Traffic Cop, and I'm talking to a worker who didn't send a grid
            //This worker will try and find nearby food.  I will do the same, and decrememnt the food on that spot.
            //This is an optimization to let later ants know there is less food on this spot than believed.
            if (this.brain.isTrafficCop() && friendKnowledge.role == AntBrain.WORKER && friendKnowledge.grid == null){
                this.brain.getPointsWithFood();
                Point nextPoint;
                if (!this.brain.foodList.isEmpty()){
                    //The point that the worker will be going to.
                    nextPoint = this.brain.foodList.remove(0);
                    Spot foodSpot = this.brain.getSpot(nextPoint);
                    foodSpot.setFood(foodSpot.food - 1, this.brain.knowledge.year);
                    AntLogger.infoLog("TC setting food value at spot " + nextPoint + " to " + foodSpot.food);
                    if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
                    return;
                }
            }
            
            //If I don't know the current year, set the current year to what the trafficCop says.
            if (friendKnowledge.year > (this.brain.knowledge.year + 1) && this.brain.knowledge.age <= 2)
                this.brain.knowledge.year = friendKnowledge.year;
            
            //If I received data from a WORKER, just abort.  WORKERS don't need to communicate.
            if (friendKnowledge.role == AntBrain.WORKER && this.brain.knowledge.year > 2){
                if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
                return;
            }
            
            //Initially assigning someone as the trafficCop
            if (this.brain.knowledge.year == 1) {
                //If the friend ant is older than me, I can't be the traffic cop.
                if (friendKnowledge.age <= this.brain.knowledge.age) {
                    //We're both kids, let's calculate who the traffic cop is.
                    if (brain.amIBetterForTrafficCop(friendKnowledge.id) && !(brain.confirmedTrafficCop()) ) {
                        brain.setAsTrafficCop();
                    }
                    else {
                        brain.setAsWorker();
                    }
                }
                else
                    brain.setAsWorker();
            }
            
            //Initially assigning someone as the Scanner
            if (this.brain.knowledge.year == 2) {
                if (!this.brain.isTrafficCop() && 
                    !this.brain.isTrafficCop(friendKnowledge.role)){
                    if (friendKnowledge.id < this.brain.knowledge.id)
                        this.brain.setAsScanner();
                    else{
                        this.brain.setAsWorker();
                        //Could assign a second scanner here.
                    }
                }
            }
            
            //Tell this scanner that it has recently talked to the TC.
            if (this.brain.knowledge.role == AntBrain.SCANNER
                    && (friendKnowledge.role == AntBrain.TRAFFIC_COP))
                this.brain.setLastTalkedToNonScanner();
            
            //For each spot on the grid, if the other ant has traveled there more recently than I, copy in their data.
            if (DEBUG) PerfMonitor.startClock(PerfMonitor.RECEIVE_LEARN, this.hashCode());
            this.brain.learn(friendKnowledge);
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.RECEIVE_LEARN, this.hashCode());
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
        }
        catch(Exception e) 
        {
            AntLogger.infoLog("Error receiving message from friendly ant: " + e);
            if (DEBUG) PerfMonitor.stopClock(PerfMonitor.METHOD_RECEIVE, this.hashCode());
        }
        
    }
    
    
    /**
     * Set up private variables and increment age.  This is done once per "day."
     */
    private void dawn() {
        if (this.myName == null)
        {
            //Being born!
            this.brain = new AntBrain();
            this.myName = getNewName();
            if (DEBUG) PerfMonitor.registerAnt(this.hashCode());
            AntLogger.infoLog("*******A STAR IS BORN***** " + this.myName);
        }
        this.brain.incAge();
        this.brain.incYear();
        this.brain.incLastTalkedToNonScanner();
    }

    /**
     * generate a new name for fun.
     * @return the new name
     */
    private String getNewName() {
        return NAMES[(int)(Math.random()*100)];
    }
    
    /**
     * Convert an ANT to a String.
     * 
     * @return String representation of the ant.
     */
    @Override
    public String toString(){
        return (myName + "@" + brain.currentPoint + " and age: " + brain.knowledge.age + " ROLE: " + brain.getRole() + " year: " + this.brain.knowledge.year);
    }
    
    long[] ticks = new long[100];
        
}

/**
 * Represents the brain of the ant, containing logic and navigation methods, and an AntKnowledge object.
 * 
 * @author Jesse Smith (jesse@steelcorelabs.com)
 */
class AntBrain {

    AntKnowledge knowledge;
    
    //Java HashCode object ID of the ant.
    private int antID;
    //Size of grid
    private int GRIDSIZE = 72;
    /** Confirmed this ant as a worker. */
    boolean confirmedTrafficCop = false;
    /** {@link Point} representing where the ant is on the grid now. */
    Point currentPoint;
    //Number of ants on this spot with me now.
    private int numAnts;
    //Am I holding food
    private boolean holdingFood = false;
    //Scanner variables to help reporting
    boolean firstFoodReportComplete = false, foundFirstFood = false;
    
    //The current route of the ant.
    private LinkedList<Move> currentRoute = null;
    
    //The point representing Home.
    private Point homePoint = null;
    
    //Should I route home immediately.
    private boolean goHome = false;
    
    //List of points that need to be searched.  Used by Scanner ants.
    private ArrayList<Point> searchList = new ArrayList();
    
    /** List of {@link Point} objects that contain food. */
    ArrayList<Point> foodList = new ArrayList();
    
    //Current distance of the SCANNER from home.
    private int threshold = 0;
    //Amount by which the threshold distance is increased.
    private int thresholdInc = 2;
    //Number of turns before the SCANNER last spoke to the TC.
    private int lastTalkedToNonScanner = 0;
    //As a SCANNER, amount of food I have not yet reported on.
    private int newFoodToReport = 0;
    //As a SCANNER, amount of food I should know about before reporting back.
    private int newFoodThreshold = 10;
    
    //Constants
    /** Ant roles */
    static final int TRAFFIC_COP = 1, WORKER = 2, SCANNER = 3;
    /** Enable route debugging. */
    static final boolean DEBUG_ROUTE = false;
    
    /** The max distance a SCANNER should ever search away from home in any direction */
    static final int MAX_THRESHOLD = 18;
    /** Base number of turns before the SCANNER should report back */
    static final int TALK_THRESHOLD = 10;
    //Age of adulthood
    private int ADULT_AGE = 2;
    
    /**
     * Constructor for the AntBrain.
     * Creates the AntKnowledge object.
     */
    public AntBrain() {
        //Ants are all born as workers, one will be assigned as the traffic cop.
        knowledge = new AntKnowledge(WORKER, GRIDSIZE);
        currentPoint = new Point(36,36);
        homePoint = new Point(36,36);
        currentRoute = new LinkedList();
    }
    
    //--------CALCULATE STUFF------
    
    /**
     * Simple method to move the ant out and back 1 space to trigger communication.
     * @return Simple move {@link Action}
     */
    public Action dance() {    
        try {
            if (knowledge.age % 2 == 1) {
                return (doMove(this.getConsistantDirection()));
            }
            findRoute(homePoint);
            Move nextMove = currentRoute.pollLast();
            return (doMove(nextMove));   
        }
        catch(Exception e){
            AntLogger.infoLog("Error Navigating home in setup " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Peek in all four directions, and write into my knowledge.
     * <p>
     * Does this spot have food? Are my four neighboring spots traversable?
     * @param surroundings Information about the four surrounding {@link Tile} tiles.
     */
    public void analyzeSurroundings(Surroundings surroundings) {
        //See what spots around me are travelable
        Tile here = surroundings.getCurrentTile();
        this.getValidTiles(surroundings);

        //Record amount of food on this spot
        int hereFood = here.getAmountOfFood();
        if (hereFood > 0 && !foundFirstFood)
            foundFirstFood = true;
        this.setFoodHere(hereFood);

        this.numAnts = here.getNumAnts();
    }
    
    /**
     * Is this ant standing at home.
     * @return True or False, is this ant currently standing at home.
     */
    public boolean atHome(){
        return (currentPoint.x == 36 && currentPoint.y == 36);
    }
    
    /**
     * Do a search to find the set of nearby tiles to explore.
     * @return "move" {@link Action} to the next closest spot that needs to be explored.
     */
    public Action search() {
        while (currentRoute.isEmpty()){
            this.localExplore();
        }
        //If i switched to a WORKER, jump over
        if (this.knowledge.role == AntBrain.WORKER){
            return (doWork());
        }
        
        if (lastTalkedToNonScanner < 2){
            currentRoute.clear();
            this.localExplore();
        }
        //Grab next move from my currentRoute
        Move nextMove = currentRoute.pollLast();
        return (doMove(nextMove));
    }
    
    /**
     * Collect food.
     * <p>
     * Calculate the nearest food that needs to be collected, or pick up and bring the food this ant is carrying home.
     * @return The {@link Action} for the ant, either DROP_OFF, or MOVE to next spot.
     */
    public Action doWork() {
        //If I'm holding food, either drop it off or bring it home.
        if (holdingFood) {
            if (this.atHome()) {
                holdingFood = false;
                currentRoute.clear();
                return (Action.DROP_OFF);
            }
            else{
                //Not holding food, so find some or go home and wait.
                try {
                    if (goHome){
                        //I should go home to get more information.
                        findRoute(homePoint);
                    }
                    if (currentRoute.isEmpty()){
                        findRoute(homePoint);
                    }
                    Move nextMove = currentRoute.pollLast();
                    //AntLogger.infoLog("NEXT MOVE FOR HOMEWARD ANT: " + nextMove);
                    return (doMove(nextMove));    
                }
                catch (Exception e){
                    AntLogger.infoLog("Error Navigating home with food");
                    return null;
                }
            }
        }
        
        //If I'm not holding food, but there is food on this spot, collect it!
        //Make sure another ant doesn't snatch it first??
        if (this.getCurrentSpot().food > 0 && !(this.atHome())) {
            holdingFood = true;
            //Set remaining food on this spot to the value after everyone here has gathered.
            this.getCurrentSpot().setFood((this.getCurrentSpot().food - this.numAnts), this.knowledge.year);
            goHome = true;
            return (Action.GATHER);
        }
        
        if (!holdingFood) {
            //AntLogger.infoLog("Worker looking for close food");
            //Find Closest food if no current route.
            if (currentRoute.isEmpty()){
                //AntLogger.infoLog("Worker building new route to food");
                this.findNextFood();
                if (currentRoute.isEmpty()){
                    //If no food known, home.
                    if (!this.atHome()){
                        findRoute(homePoint);
                    }
                    return(Action.HALT);
                }
            }
            Move nextMove = currentRoute.pollLast();
            return (doMove(nextMove));
        }
        //This shouldn't ever happen!! Throw error.
        return (this.dance());
    }
    
    //--------NAVIGATE STUFF-----
    /**
     * Calculate all neighboring {@link Tile}s that are traversable.
     * @param basePoint The base {@link Point} from which to calculate valid neighbors.
     * @return The ArrayList containing {@link Move} objects for the valid neighbors.
     */
    public ArrayList getAllValidMoves(Point basePoint) {
        ArrayList list = new ArrayList();
        for (Direction d : Direction.values())
        {
            Point newPoint = this.movePoint(basePoint, d);
            Spot trySpot = this.getSpot(newPoint);
            if (trySpot != null){ //Spot must be on grid, or else it returns null
                if (trySpot.traversable)
                    list.add(new Move(newPoint));
            }
        }
        return list;
    }
    
    /**
     * Get a valid {@link Direction} for this spot, and consistently return the same {@link Direction} on subsequent calls.
     * @return A {@link Direction} in which to move.
     */
    private Direction getConsistantDirection() {
        for (Direction d : Direction.values())
        {
            Spot trySpot = this.getSpot(currentPoint, this.directionToGrid(d));
            if (trySpot != null){ //Spot must be on grid, or else it returns null
                if (trySpot.traversable)
                    return(d);
            }
        }
        return (null);
    }
    
    /**
     * Modify this ant's currentPoint {@link Point} in {@link Direction} of d.
     * @param d {@link Direction} in which to move.
     * @return The same {@link Direction} as given as parameter
     */
    private Direction move(Direction d){
        if (d.name().compareTo("NORTH") == 0) {
            currentPoint.y++;
        }
        if (d.name().compareTo("SOUTH") == 0) {
            currentPoint.y--;
        }
        if (d.name().compareTo("EAST") == 0) {
            currentPoint.x++;
        }
        if (d.name().compareTo("WEST") == 0) {
            currentPoint.x--;
        }
        return d;    
    }
    
    /**
     * Convert a {@link Direction} to a distance 1 {@link Point}.
     * @param d The {@link Direction} used.
     * @return {@link Point} object of distance 1.
     */
    private Point directionToGrid(Direction d) {
        Point p = null;
        if (d.name().compareTo("NORTH") == 0) {
            p = new Point(0,1);
        }
        if (d.name().compareTo("SOUTH") == 0) {
            p = new Point(0,-1);
        }
        if (d.name().compareTo("EAST") == 0) {
            p = new Point(1,0);
        }
        if (d.name().compareTo("WEST") == 0) {
            p = new Point(-1,0);
        }
        return p;
    }
    
    /**
     * Convert a distance 1 {@link Point} p to {@link Direction} object.
     * @param p input {@link Point}.
     * @return {@link Direction} representing that {@link Point} direction.
     */
    private Direction gridToDirection(Point p) {
        if (p.y == 1)
            return Direction.NORTH;
        if (p.y == -1)
            return Direction.SOUTH;
        if (p.x == 1)
            return Direction.EAST;
        if (p.x == -1)
            return Direction.WEST;
        
        return null;
    }
    
    /**
     * Wrapper for converting Direction to "move" {@link Action}.
     * @param d the Direction in which to move.
     * @return {@link Action} object for that direction.
     */
    private Action doMove (Direction d) {
        return Action.move(this.move(d));
    }
    
    /**
     * Wrapper for converting {@link Move} object to "move" {@link Action}.
     * @param m the {@link Move} object to move the ant.
     * @return {@link Action} for the ant to move.
     */
    private Action doMove (Move m) {
        return Action.move(this.move(this.gridToDirection(this.subtractPoints(currentPoint, m.movePoint))));
    }
    
    //--------MISC STUFF---------
    /**
     * Is this ant the Traffic Cop
     * @return True or False if this ant is the Traffic Cop.
     */
    public boolean isTrafficCop() {
        return (this.knowledge.role == TRAFFIC_COP);
    }
    
    /**
     * Does the identified role represent a Traffic Cop
     * @param p_role Input role of ant in question.
     * @return True or False if the role parameter represents a Traffic Cop.
     */
    public boolean isTrafficCop(int p_role) {
        return (p_role == TRAFFIC_COP);
    }
    
    /**
     * Checks the neighboring {@link Tile}s and updates the AntKnowledge for traversable appropriately.
     * @param p_surroundings Surroundings object containing information about the neighbors.
     */
    private void getValidTiles(Surroundings p_surroundings) {
        for (Direction c : Direction.values())
        {    
            Spot currSpot = this.getSpot(currentPoint, this.directionToGrid(c));
            if (p_surroundings.getTile(c).isTravelable())
                currSpot.setTraversable(true, knowledge.year);
            else
                currSpot.setTraversable(false, knowledge.year);
        }
    }
    
    /**
     * Set food for this spot on the grid, and update the year seen.
     * @param seenFood Amount of food seen on this spot.
     */
    private void setFoodHere(int seenFood){
        Spot spot = this.getCurrentSpot();
        if (spot.food == -1)
            newFoodToReport += seenFood;
        spot.setFood(seenFood, knowledge.year);
    }
    
    /**
     * Return a Spot object representing this ant's current {@link Tile}.
     * @return The Current Spot.
     */
    public Spot getCurrentSpot() {
        return (knowledge.grid[currentPoint.x][currentPoint.y]);
    }
    
    /**
     * Return the spot object located on the grid at the location identified by adding the current location to the input x and y parameters.
     * @param transX Amount to move in the X direction from the current {@link Point}.
     * @param transY Amount to move in the Y direction from the current {@link Point}.
     * @return The Spot being requested.
     */
    public Spot getSpot(int transX, int transY) {
        return (knowledge.grid[currentPoint.x + transX][currentPoint.y + transY]);
    }
    
    /**
     * Return the Spot object at the point given.
     * @param somePoint Point to find the Spot at.
     * @return The Spot being requested.
     */
    public Spot getSpot(Point somePoint){
        return (knowledge.grid[somePoint.x][somePoint.y]);
    }
    
    /**
     * Return the Spot object at the base {@link Point} added to the transform {@link Point}.
     * @param basePoint Start at this location on the grid.
     * @param transPoint Modify by this amount, combining X and Y values.
     * @return The Spot being requested.
     */
    public Spot getSpot(Point basePoint, Point transPoint){
        int newX = basePoint.x + transPoint.x;
        int newY = basePoint.y + transPoint.y;
        if ( Math.abs(newX) >= GRIDSIZE 
                || Math.abs(newY) >= GRIDSIZE)
            return null;
        return (knowledge.grid[newX][newY]);
    }
    
    /**
     * Get a new {@link Point} by moving the basePoint in the {@link Direction} d.
     * @param basePoint Base {@link Point} for the operation.
     * @param d {@link Direction} in which to move the basePoint.
     * @return New {@link Point}.
     */
    public Point movePoint(Point basePoint, Direction d){
        Point directionAsPoint = this.directionToGrid(d);
        return (this.addPoints(basePoint, directionAsPoint));
    }
    
    /**
     * Calculate a new {@link Point} by combining the X and Y values of the two points.
     * @param p1 {@link Point} 1.
     * @param p2 {@link Point} 2.
     * @return New {@link Point} representing the combination of X and Y values of the two points.
     */
    public Point addPoints(Point p1, Point p2){
        int newX = p1.x + p2.x;
        int newY = p1.y + p2.y;
        if ( Math.abs(newX) >= GRIDSIZE || Math.abs(newY) >= GRIDSIZE)
            return null;
        return (new Point(newX, newY));
    }
    
    /**
     * Calculate a new {@link Point} by subtracting the X and Y values of {@link Point} 1 from {@link Point} 2.
     * @param p1 Point 1.
     * @param p2 Point 2.
     * @return New Point representing the difference of p2 and p1.
     */
    public Point subtractPoints(Point p1, Point p2){
        return (new Point(p2.x - p1.x, p2.y - p1.y));
    }
    
    /**
     * Increate age of this ant by 1 year.
     */
    public void incAge() {
        ++knowledge.age;
    }
    
    /**
     * Increate the current year by 1.
     */
    public void incYear() {
        ++knowledge.year;
    }
    
    /**
     * Returns a boolean representing if this ant is better suited to be the Traffic Cop than the one passed in in the parameter.
     * <p>
     * The algorithm for handling this calculation is currently by comparing the hashCodes of the objects, lowest wins.
     * @param otherAntID hashcode of other ant.
     * @return Boolean representing if this ant is a better candidate to be the Traffic Cop.
     */
    public boolean amIBetterForTrafficCop(int otherAntID) {
        return (knowledge.id < otherAntID);
    }
    
    /**
     * Set this ant's role as TRAFFIC_COP.
     */
    public void setAsTrafficCop(){
        knowledge.role = TRAFFIC_COP;
    }
    
    /**
     * Set this ant's role as WORKER.
     * <p>
     * Internally this sets a confirmation flag to true.
     */
    public void setAsWorker(){
        knowledge.role = WORKER;
        confirmedTrafficCop = true;
    }
    
    /**
     * Set the ant's role to SCANNER.
     */
    public void setAsScanner(){
        knowledge.role = SCANNER;
    }
    
    /**
     * Has this ant had it's role confirmed.
     * @return Boolean representing if this ant has had it's role confirmed.
     */
    public boolean confirmedTrafficCop() {
        return confirmedTrafficCop;
    }
    
    /**
     * Setter for the amount of food to be reported.
     * @param newFood Amount of food to be reported.
     */
    public void setNewFoodToReport(int newFood) {
        this.newFoodToReport = newFood;
    }
    
    /**
     * Is the ant in it's Youth stage.
     * <p>
     * This is used to do initial calculations on the ant, such as setting the role.
     * @return Boolean representing if this ant is in youth.
     */
    public boolean inYouth() {
        return (this.knowledge.age <= ADULT_AGE);
    }
    
    /**
     * Reset the years since this ant last spoke to the Traffic Cop.  
     * For scanners only.
     */
    public void setLastTalkedToNonScanner(){
        lastTalkedToNonScanner = 0;
    }
    
    /**
     * Increment years since last talking to Traffic Cop.
     * For Scanners only.
     */
    public void incLastTalkedToNonScanner(){
        lastTalkedToNonScanner++;
    }
    
    /**
     * Increment the search threshold by the thresholdInc amount.
     */
    private void incThreshold(){
        threshold += this.thresholdInc;
    }
    
    /**
     * Get a friendly name for this ant's role.
     * @return String representation of this ant's role.
     */
    public String getRole(){
        switch (knowledge.role) {
            case AntBrain.WORKER:
                return ("WORKER");
            case AntBrain.TRAFFIC_COP:
                return ("TRAFFIC_COP");
            case AntBrain.SCANNER:
                return ("SCANNER");
            default:
                return ("UNKNOWN ROLE!!");
                //Throw exception
        }
    }
    
    /**
     * For new information in the friend ant's grid, copy into this ant's grid.
     * @param friendGrid Grid from ant friend.
     */
    public void learn(AntKnowledge friendGrid) {
        if (friendGrid == null){
            return;
        }
        for (int x=0; x<GRIDSIZE; x++)
        {
            for (int y=0; y<GRIDSIZE; y++)
            {
                Spot mySpot = this.knowledge.grid[x][y];
                Spot friendSpot = friendGrid.grid[x][y];
                if (mySpot.yearViewed < friendSpot.yearViewed)
                    mySpot.copyViewData(friendSpot);
                if (mySpot.yearVisited < friendSpot.yearVisited)
                    mySpot.copyVisitData(friendSpot);
            }
        }
    }
    
    /**
     * Running this method sets the foodList list variable with known grid spots with food, sorted by distance from CurrentPoint {@link Point}.
     */
    public void getPointsWithFood() {
        foodList.clear();
        for (int i=0; i < GRIDSIZE; i++){
            for (int j=0; j < GRIDSIZE; j++){
                if (i==36 && j==36)
                    continue;
                
                Spot currSpot = this.knowledge.grid[i][j];
                
                if (currSpot.food > 0)
                    foodList.add(new Point(i,j));
            }
        }
        //Sort by distance to currentpoint.
        Collections.sort(foodList, CURR_DISTANCE_ORDER);
    }
    
    /**
     * Clear current foodList list variable and rebuild.
     */
    public void findNextFood() {
        Point nextPoint;
        //Always update the food list, as this ant might have new info.
        foodList.clear();
        this.getPointsWithFood();

        if (foodList.isEmpty())
            return;
        nextPoint=foodList.remove(0);
        this.findRoute(nextPoint);
    }
    
    /**
     * Calculate non-visited {@link Point} objects on the Grid within the current threshold distance.
     * Update the searchList list variable with the valid points, sorted by distance to CurrentPoint {@link Point}.
     * @param threshold Current distance threshold to search within.
     */
    public void getUnexploredThresholdPoints (int threshold){
        //AntLogger.infoLog("Building new Threshold Points list");
        int lowerBound = this.GRIDSIZE/2 - threshold;
        int upperBound = this.GRIDSIZE/2 + threshold;
        for (int i=lowerBound; i <= upperBound; i++){
            for (int j=lowerBound; j <= upperBound; j++){
                Spot currSpot = this.knowledge.grid[i][j];
                
                if (currSpot.yearVisited >= 0 || (currSpot.yearViewed >=0 && !(currSpot.traversable)));
                else
                    searchList.add(new Point(i,j));
            }
        }
        Collections.sort(searchList, CURR_DISTANCE_ORDER);
    }
    
    /**
     * Build a plan for exploring the map.
     * <p>
     * This algorithm finds non-visited spots within a slowly increasing distance.  Those spots are sorted by distance.
     * <p>
     * Once the list is available, the ant moves to the closest spot.  On the next turn, the list is resorted by distance to currentPoint, and the ant moves to the closest spot. 
     * Since the spots are routed to one space at a time, there should always be a success method to route to the spot, unless there is an obstacle.
     * <p>
     * If there are no more non-visited spots in the list, the threshold is increased and the algorithm is repeated.
     * <p>
     * If a {@link Tile} on the list is untraversable, it is makes as such and removed from the list.
     * If a {@link Tile} on the list can't even be viewed because of an obstacle and the route to it is either impossible or outside of the current search threshold, 
     * it is removed from the list.  It will be put back on the list in the next iteration when the threshold has been increased.
     * This method will rarely fail to traverse any valid squares.  However, it can happen.  An optimization could be made to handle this rare edge case.
     */
    public void localExplore() {
        
        Spot nextSpot;
        Point nextPoint;
        boolean foundNextMove = false;
        
        //Run home
        //UNLESS I"VE CHATTED TO A WORKER OR TC RECENTLY
        if ((!firstFoodReportComplete && foundFirstFood) || ((newFoodToReport >= newFoodThreshold) && (lastTalkedToNonScanner > TALK_THRESHOLD + ((int)(this.knowledge.year / 5))))){
            //AntLogger.infoLog("-----------TIME TO HEAD HOME---------");
            if (!firstFoodReportComplete)
                firstFoodReportComplete = true;
            this.findRoute(homePoint);
            return;
        }
        
        while (!foundNextMove){
            //Add to the searchList.
            if (searchList.isEmpty()){
                this.incThreshold();
                //Max distance in any given direction is max of 18
                if (threshold > MAX_THRESHOLD){
                    //Finished checking map!  Set this ant as a worker and start working.
                    this.knowledge.role = AntBrain.WORKER;
                    return;
                }
                else
                    this.getUnexploredThresholdPoints(threshold);
            }

            Collections.sort(searchList, CURR_DISTANCE_ORDER);
            nextPoint=searchList.remove(0);
            nextSpot=this.getSpot(nextPoint);
            if ((nextSpot.yearViewed >= 0 && !nextSpot.traversable) || nextSpot.yearVisited >= 0){
                //AntLogger.infoLog("Not List exploring point " + nextPoint + " because no need");
            }
            else{
                //AntLogger.infoLog("List Exploring to point " + nextPoint);
                foundNextMove = this.findRoute(nextPoint);
            }
        }
    }
    
    //A* implementation
    /**
     * An implementation of the A* routing algorithm.  
     * This calculates the shortest distance from the currentpoint {@link Point} to the target {@link Point}.
     * This algorithm only uses known {@link Tile}s, and won't help during exploration.
     * <p>
     * After this method runs, the currentRoute list variable holds the series of points to get from here to target.
     * <p>
     * The implementation used here was based on the following:
     * http://www.policyalmanac.org/games/aStarTutorial.htm
     * <p>
     *
     * 
     * @param targetPoint Target {@link Point} to route to.
     * @return True if a route to the point was successfully calculated.  False otherwise.
     */
    public boolean findRoute(Point targetPoint) {
        
        if (DEBUG_ROUTE) AntLogger.infoLog("Finding route from point " + currentPoint + " to " + targetPoint);
                
        if (currentRoute != null && !currentRoute.isEmpty())
            currentRoute.clear();
        
        ArrayList<Move> openList = new ArrayList();
        LinkedList<Move> closedList = new LinkedList();
        
        Move start = new Move(
                calculateCost(currentPoint, currentPoint),
                calculateCost(currentPoint, targetPoint),
                currentPoint, null);
        openList.add(start);
        
        while (!openList.isEmpty()) {
            Move currentMove = (Move)openList.remove(0);
            if (DEBUG_ROUTE) AntLogger.infoLog("Checking element " + currentMove);
            if (DEBUG_ROUTE) AntLogger.infoLog("OpenList size: " + openList.size());
            if (DEBUG_ROUTE) AntLogger.infoLog("printing open list: ");
            if (DEBUG_ROUTE) this.printRoute(openList);
            if (currentMove.movePoint.equals(targetPoint)){
                currentRoute.add(currentMove);
                //AntLogger.infoLog("Found TARGET!!");
                break;
            }
            openList.remove(currentMove);
            closedList.add(currentMove);
            
            //Get valid moves.  These have a point, but have no costs associated, nor parent links
            ArrayList<Move> validMoves = getAllValidMoves(currentMove.movePoint);
            boolean moveAlreadyOnClosedList, moveAlreadyOnOpenList;
            
            for (Move checkMove : validMoves){
                //Is the checkMove the TARGET??
                moveAlreadyOnClosedList = false;
                moveAlreadyOnOpenList = false;
                if (DEBUG_ROUTE) AntLogger.infoLog("Checking Valid Move: " + checkMove);
                
                for (Move closedListMove : closedList) {
                    if (checkMove.isEqualTo(closedListMove)){
                        moveAlreadyOnClosedList = true;
                        if (DEBUG_ROUTE) AntLogger.infoLog("Move is on closed list!");
                        break;
                    }
                }
                if (moveAlreadyOnClosedList){
                    if (DEBUG_ROUTE) AntLogger.infoLog("Breaking out of closed list");
                    continue;
                }   
                
                for (Move openListMove : openList) {
                    if (checkMove.isEqualTo(openListMove)) {
                        moveAlreadyOnOpenList = true;
                        if (DEBUG_ROUTE) AntLogger.infoLog("Move is on Open List!");
                        if ( (currentMove.sourceCost + 1) < openListMove.sourceCost) {
                            //Have to remove and add the element, so it will resort :(
                            if (DEBUG_ROUTE) AntLogger.infoLog("But this route is better");
                            openList.remove(openListMove);
                            openListMove.parent = currentMove;
                            openListMove.sourceCost = currentMove.sourceCost + 1;
                            openList.add(openListMove);
                            Collections.sort(openList, MOVE_ORDER);
                        }
                        break;
                    }
                }
                if (!moveAlreadyOnOpenList){
                    if (DEBUG_ROUTE) AntLogger.infoLog("Adding move to open list");
                    //Check Move is not on the openList yet, so add it.
                    checkMove.setCosts(currentMove.sourceCost + 1,
                        calculateCost(checkMove.movePoint, targetPoint));
                    checkMove.parent = currentMove;
                    if (DEBUG_ROUTE) AntLogger.infoLog("OpenList size: " + openList.size());
                    boolean status = openList.add(checkMove);
                    if (DEBUG_ROUTE) AntLogger.infoLog("adding element is: " + status);
                    Collections.sort(openList, MOVE_ORDER);
                    if (DEBUG_ROUTE) AntLogger.infoLog("Now OpenList size: " + openList.size());
                }
            }
        }
        if (openList.isEmpty()){ 
            //No route to target, leave currentRoute empty
            if (DEBUG_ROUTE) AntLogger.infoLog("Unable to reach destination, failure building route");
            return false;
        }
        else{
            //Build the best route home by following parents.
            if (DEBUG_ROUTE) AntLogger.infoLog("Found the route to target" + targetPoint);
            Move nextMove = (Move)currentRoute.get(0);
            while (nextMove.parent != null) {
                currentRoute.add(nextMove.parent);
                nextMove = nextMove.parent;
            }
            //Pull the last value.
            currentRoute.pollLast();
            if (DEBUG_ROUTE) AntLogger.infoLog("FInal route is: ");
            if (DEBUG_ROUTE) this.printRoute(currentRoute);
            return true;
        }
    }
    
    /**
     * Calculate the direct distance between two points.
     * @param s First {@link Point}.
     * @param e Second {@link Point}.
     * @return Distance between points. Distance in X + distance in Y.
     */
    private int calculateCost(Point s, Point e){
        return (Math.abs(s.x - e.x) + Math.abs(s.y - e.y));
    }
    
    /**
     * Loop through the route given, printing each point.
     * @param route Route to print to the log.
     */
    public void printRoute(LinkedList<Move> route){
        AntLogger.infoLog("The route");
        int count = 1;
                
        for (Move m : route){
            if (count % 3 == 0)
                AntLogger.infoLog(m.movePoint + " - ");
            else
                AntLogger.infoLog(m.movePoint + " - ");
            count++;
        }
    }
    
     /**
     * Loop through the route given, printing each point.
     * @param route Route to print to the log.
     */
    public void printRoute(ArrayList<Move> route){
        AntLogger.infoLog("The List");
        int count = 1;
                
        for (Move m : route){
            if (count % 3 == 0)
                AntLogger.infoLog(m.movePoint + ":" + m.getTotalCost() + " - ");
            else
                System.out.print(m.movePoint + ":" + m.getTotalCost() + " - ");
            count++;
        }
        AntLogger.infoLog("");
    }
    
    /**
     * Comparator to calculate the distance from a {@link Point} to the currentPoint.
     */
    final Comparator<Point> CURR_DISTANCE_ORDER = new Comparator<Point>() {
        @Override
        public int compare(Point e1, Point e2) {
            int coste1 = (Math.abs(e1.x - currentPoint.x) + Math.abs(e1.y - currentPoint.y));
            int coste2 = (Math.abs(e2.x - currentPoint.x) + Math.abs(e2.y - currentPoint.y));
            return (coste1 - coste2);
        }
    };
    
    /**
     * Comparator to calculate the distance between two {@link Move} objects.
     */
    final Comparator<Move> MOVE_ORDER = new Comparator<Move>() {
        @Override
        public int compare(Move e1, Move e2) {
            return (e1.getTotalCost() - e2.getTotalCost());
        }
    };
}

/**
 * AntKnowledge class holds the minimal important data to be transfered between ants during communication.
 * 
 * This class implements {@link Seriablizable} so it can easily be transfered as a byte[].
 * 
 * @author Jesse Smith (jesse@steelcorelabs.com)
 */
class AntKnowledge implements Serializable {
    
    Spot[][] grid;
    int age = 0;
    int year = 0; //The current year of this colony
    int role;
    int id;
    
    /**
     * Initialize the {@link AntKnolwedge} object with a role and a gridsize.
     * @param p_role Role of this ant.
     * @param gridSize Grid size for the grid of this ant.
     */
    public AntKnowledge(int p_role, int gridSize) {
        age = 0;
        year = 0;
        role = p_role;
        id = this.hashCode();
        grid = new Spot[gridSize][gridSize];
        for (int x=0; x < gridSize; x++)
            for (int y=0; y < gridSize; y++)
                grid[x][y] = new Spot();
    }
    
    /**
     * Initialize the {@link AntKnolwedge} object with a role.
     * <p>
     * This constructor is used to create a temporary AntKnowledge that doesn't have a grid.
     * @param p_role Role of this ant.
     * @param gridSize Grid is null.
     */
    public AntKnowledge(int p_role) {
        age = -1;
        year = -1;
        role = p_role;
        id = -1;
        grid = null;
    }
}

/**
 * Spot class represents the information about a single spot {@link Tile} on the map.
 * @author Jesse Smith (jesse@steelcorelabs.com)
 */
class Spot implements Serializable {
    /**
     * Constructor for Spot objects.
     */
    public Spot () {
        food = -1; //If I haven't learned about this spot yet, it has -1 food.
        traversable = false; //Can I walk on this spot?
        yearViewed = -1;  //The day at which this spot was last analyzed
        yearVisited = -1; //The day at which this spot was last visited (to check for food)
    }
        
    int food;
    boolean traversable;
    int yearViewed;
    int yearVisited;
    
    /**
     * Copy the visit data for another {@link Spot}.
     * <p>
     * Internally, update the year visited for this spot to that of the source Spot.
     * @param s Source {@link Spot}.
     */
    public void copyVisitData(Spot s) {
        this.food = s.food;
        this.yearVisited = s.yearVisited;
    }
    
    /**
     * Copy the viewed data for another {@link Spot}.
     * <p>
     * Internally, update the year viewed for this spot to that of the source Spot.
     * @param s Source {@link Spot}
     */
    public void copyViewData(Spot s) {
        this.traversable = s.traversable;
        this.yearViewed = s.yearViewed;
    }
    
    /**
     * Getter for the food amount.
     * @return amount of food.
     */
    public int getFood() {
        return food;
    }

    /**
     * Setter for food, and the year it was originally learned.
     * @param food The amount of food.
     * @param year The year it was learned.
     */
    public void setFood(int food, int year) {
        this.food = food;
        yearVisited = year;
    }
    
    /**
     * Setter for traversable variable, and the year it was originally learned.
     * @param isTraversable Whether the spot is traversable.
     * @param year The year it was learned.
     */
    public void setTraversable(boolean isTraversable, int year){
        this.traversable = isTraversable;
        yearViewed = year;
    }
}

//A move is a point, and the parent point, and the est_cost to get to the target from this point.
/**
 * Class representing a Move.
 * <p>
 * A {@link Move} consists of a {@link Point} movePoint, a {@link Point} parentPoint, 
 * an estimated cost for the movePoint, and an estimated cost for the parentPoint.
 * <p>
 * This class is helpful for the implementation of the A* routing algorithm.
 * @author Jesse Smith (jesse@steelcorelabs.com)
 */
class Move {
    int destCost = -1, sourceCost = -1;
    Point movePoint = null;
    Move parent = null;
    
    /**
     * Constructor of {@link Move} class.
     * Sets initial parameters.
     * @param p_sourceCost Cost to get from starting point to this parentPoint.
     * @param p_destCost Estimated cost to get from movePoint to final target point.
     * @param p_movePoint Core point for this Move.
     * @param p_parent Parent point that comes before the current movePoint in the route.
     */
    public Move(int p_sourceCost, int p_destCost, Point p_movePoint, Move p_parent){
        sourceCost = p_sourceCost;
        destCost = p_destCost;
        parent = p_parent;
        movePoint = p_movePoint;
    }
    
    /**
     * Constructor for {@link Move} that doesn't include costs.
     * @param p_movePoint Core point for this Move.
     */
    public Move(Point p_movePoint){
        movePoint = p_movePoint;
    }
    
    /**
     * Constructor for {@link Move} that doesn't include costs.
     * @param p_movePoint Core point for this Move.
     * @param p_parent Parent point that comes before the current movePoint in the route.
     */
    public Move(Point p_movePoint, Move p_parent){
        parent = p_parent;
        movePoint = p_movePoint;
    }
        
    /**
     * Two moves are the same if they represent the same {@link Point}
     * @param compareMove The move to compare to this one.
     * @return True or False if the two moves represent the same {@link Point}.
     */
    public boolean isEqualTo (Move compareMove){
        return (this.movePoint.equals(compareMove.movePoint));
    }
    
    /**
     * Get the cost from the source to the Parent point, plus the estimated cost from Move point to target.
     * @return Total cost.
     */
    public int getTotalCost(){
        return (sourceCost + destCost);
    }
    
    /*
     * Setters for costs
     */
    public void setCosts(int p_sourceCost, int p_destCost){
        sourceCost = p_sourceCost;
        destCost = p_destCost;
    }
    
    /**
     * Override toString for Move objects.
     * @return String representation of Move object.
     */
    @Override
    public String toString(){
       return ("MOVE: Point:" + this.movePoint + " - SourceCost:" + this.sourceCost + 
               " - DestCost:" + this.destCost + " - ParentPoint:" + 
               ((this.parent == null) ? "null" : this.parent.movePoint)); 
    }

}

/**
 * Tracking performance for various segments of the MyAnt class.
 * <p>
 * This class contains many static methods and variables so they can track across all ants.
 * @author jessesmith
 */
class PerfMonitor {
    private static long[] totals =  new long[100];
    private static boolean hasBeenSetup = false;
    private static HashMap antData = new HashMap(50); //initialize with estimated number of ants
    //private static ArrayList antData = new ArrayList();
    private static int numCategories = 8;
    static int METHOD_ANALYZE_SURROUNDINGS = 0, METHOD_SEND = 1, METHOD_RECEIVE = 2,
        METHOD_SEARCH = 3, METHOD_DO_WORK = 4, SEND_BUNDLE = 5, RECEIVE_LEARN = 6,
        RECEIVE_BUNDLE = 7;
  
    /**
     * Used to register a new ant into the hashmap after creation.
     * @param antId The hashcode of the new ant.
     */
    public static synchronized void registerAnt(int antId){
        AntLogger.infoLog("Registering new ant: " + antId);
        PerfMonitor.registerAntIfNew(antId);
        
        if (!PerfMonitor.hasBeenSetup){
            try{
                hasBeenSetup = true;

                for (long val : totals)
                    val = 0;
            }
            catch(Exception e){
                AntLogger.infoLog("Error registering Ant " + e);
            }
        }
    }
    
    /**
     * Helper method that actually makes the new entry in the hashmap.
     * @param antId hashcode of the new ant.
     */
    private static void registerAntIfNew(int antId){
        if (antData.containsKey(antId))
            return;
        else {
            long[] antTicks = new long[numCategories];
            for (long val : antTicks)
                    val = 0;
            PerfMonitor.antData.put(antId, antTicks);
        }
    }
    
    /**
     * Increment the global counter after this ant's iteration is complete.
     * @param elem Category being tracked.
     * @param changeTicks Number of millis to increase it by.
     */
    private static void incClock(int elem, long changeTicks){
        totals[elem] += changeTicks;
    }
    
    /**
     * Starts a counter to track a segment of code for a specific ant.
     * @param category Category being tracked.
     * @param antId hashcode of the ant.
     * @throws PerformanceException 
     */
    public static void startClock(int category, int antId) throws PerformanceException {
        //Object thisAntData = antData.get(antId);
        try{
            long[] counter = (long[])antData.get(antId);
            if (counter==null){
                AntLogger.infoLog("Ant hasn't registered yet, so register now.");
                PerfMonitor.registerAntIfNew(antId);
                counter = (long[])antData.get(antId);
            }
            if (counter[category] > 0){
                throw new PerformanceException("Starting performance clock on object/category that was already started!");
            }
            counter[category] = System.currentTimeMillis();
        }
        catch (Exception e){
            AntLogger.infoLog("Error finding antData and starting timer. AntId: " + antId + " - Category: " + category + " - error:" + e);
        }
    }
    
    /**
     * Stops a counter tracking a segment of code for a specific ant.
     * @param category Category being tracked.
     * @param antId hashcode of the ant.
     */
    public static void stopClock(int category, int antId){
        long[] counter = (long[])antData.get(antId);
        long time = System.currentTimeMillis() - counter[category];
        counter[category] = 0;
        PerfMonitor.incClock(category, time);
    }
    
    /**
     * Make an entry in the log for the current running totals of the different code segments across all ants.
     * <p>
     * Only one ant should call this, likely the traffic cop.
     * @param year The current year.
     */
    public static void log(int year){
        String logString = year + " -- METHOD_ANALYZE_SURROUNDINGS:" + totals[0] + " - METHOD_SEND:" + totals[1] 
                + " - SEND_BUNDLE:"+ totals[5] + " - METHOD_RECEIVE:" + totals[2] + " - RECEIVE_LEARN:" 
                + totals[6] + " - METHOD_BUNDLE:" + totals[7] + " - METHOD_SEARCH:" + totals[3] + " - METHOD_DO_WORK:" + totals[4];
        AntLogger.infoLog(logString);
    }
}

/**
 * Static logger class to be used for logging throughout myAnt class.
 * @author jessesmith
 */
class AntLogger {
    private static Logger logger;
    private static FileHandler fh;
    private static boolean hasBeenSetup = false;
    private static boolean DEBUG = false;
    
    /**
     * Initialize logger handlers, do this once.
     * @param debugFlag 
     */
    public static synchronized void setup(boolean debugFlag){
        if (!hasBeenSetup){
            try{
                AntLogger.DEBUG = debugFlag;
                AntLogger.logger = Logger.getLogger("");
                AntLogger.fh = new FileHandler("./AntsLogFile.log");
                AntLogger.logger.addHandler(fh);  
                Handler[] ha = AntLogger.logger.getHandlers();
                //Remove default console handler
                AntLogger.logger.removeHandler(ha[0]);
                //logger.setLevel(Level.ALL);  
                SimpleFormatter formatter = new SimpleFormatter();  
                AntLogger.fh.setFormatter(formatter);
                hasBeenSetup = true;
            }
            catch (Exception e){
                AntLogger.infoLog("Error Creating Logger");
            }
        }
    }
    
    /**
     * Log to logger at level INFO
     * @param logString String to be logged.
     */
    public static synchronized void infoLog(String logString){
        if (AntLogger.hasBeenSetup) {
            if (DEBUG) AntLogger.logger.info(logString);
        }
    }
}

/**
 * Exception used for navigation methods.
 * @author jessesmith
 */
class NavException extends Exception {
    
    public NavException(String message){
        super(message);
    }
}

/**
 * Exception used for PerfMonitor methods.
 * @author jessesmith
 */
class PerformanceException extends Exception {
    
    public PerformanceException(String message){
        super(message);
    }
}