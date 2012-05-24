                                                                     
                                                                     
                                                                     
                                             
Let's get these little buggers munching!

------------
The goal of MyAnt.java is to implement the ants.Ant class, and provide control and communication systems for the independent Ant objects.  My application is structured in the following way.

Public Class:
MyAnt - Mainly used to override the ants.Ant methods.  Also responsible for identifying the "role" (Scanner, Worker, or Traffic Cop) of an ant, and keeping track of the current year (1 year passes per round).

Inner Classes:
AntBrain - Represents the brain of the ant, containing logic and navigation methods, and an AntKnowledge object.
AntKnowledge - Represents the knowledge of this ant, e.g. the map, this ant's age, role, id, etc.
Spot - Represents what an ant can know about a tile on the map.
Move - Contains two tiles, a source and a destination, and some associated costs with this move.  This is especially useful for the routing algorithm.
PerfMonitor - A static class used simply to profile the overhead / performance of various segments of code.
AntLogger - A simple, static logging class.
NavException - Navigation exceptions.
PerformanceException - Exceptions for the PerfMonitor class.

------------
The goal of the project was to have independent robot-like objects explore a map and collect resources.  The constraints were that interchange of knowledge between the robots (ants) could only happen through the use of send-receive methods that trigger when ants occupy the same tile, the map had obstacles, and more.

The major problems to solve were the following:

1. Implementing an algorithm to navigate the map between known points in the shortest time possible.

2. Designing and implementing an algorithm to search the map with efficiency.

3. Deciding what to communicate between the ants (and do it efficiently), and how to get them to communicate often enough.

------------
The plan for managing communication between the ants.

In order to best manage the different ants, they have each been assigned a role.  

Traffic Cop - One ant responsible for managing communication between all ants.  The Traffic Cop sits at home, listens for the scanner, and sends the up-to-date map to each newborn ant.

Scanner - One ant responsible for exploring the map.  The scanner slowly increases it's search radius around home, reporting to the Traffic Cop when it has some useful information about located food.

Worker - All remaining ants are workers.  Workers calculate the closest food and head off to gather it.  If there is no known food, they wait at home.

------------
The plan for exploring the map
This algorithm finds non-visited spots within a slowly increasing radius from home.  Those spots are sorted by distance and added to a list.

Once the list is available, the ant moves to the closest spot on the list.  On the next turn, the list is resorted by distance to currentPoint, and the ant again moves to the closest spot. 
Since the spots being routed to are only one space beyond an already visited space, the route should always success to route to the spot, unless there is an obstacle.

If there are no more non-visited spots in the list, the threshold is increased and the algorithm is repeated.

If a {@link Tile} on the list is untraversable, it is marked as such and removed from the list.
If a {@link Tile} on the list can't even be viewed because of an obstacle and the route to it is either impossible or outside of the current search threshold, it is removed from the list.  It will be put back on the list in the next iteration when the threshold has been increased.
This method will rarely fail to traverse any valid squares.  However, it can happen.  An optimization could be made to handle this rare edge case.

------------
The plan for routing to known spots on the map.
An implementation of the A* routing algorithm.  
This calculates the shortest distance from the currentpoint {@link Point} to the target {@link Point}.
This algorithm only uses known {@link Tile}s, and won't help during exploration.

After this method runs, the currentRoute list holds the series of points to get from here to target.

The implementation used here was based on the following:
http://www.policyalmanac.org/games/aStarTutorial.htm

@param targetPoint Target {@link Point} to route to.
@return True if a route to the point was successfully calculated.  False otherwise.

------------
Other Notes:
During communication, the entire {@link AntKnowledge} "knowledge" object of the sending ant is transferred to the receiver.
Any information updated more recently on the received grid will overwrite the contained grid.

When the program began running slowly, I inserted performance timers throughout the various methods.  This helped me gauge where the problem areas were.
By far, the biggest delays were in the "send" and "receive" methods.  Serializing the large GRID objects that each ant carried was a slow operation.
The options were to either 1. Send only the subset of spots that had been updated recently by this ant, 2. Send less frequently, or 3. Both.
The first would likely have been the most valuable, realizing the gain without losing information.  I chose the second because it was
quicker and didn't diminish the quality significantly.

The search algorithm in the Brain.localExplore method does a good job of searching the map.  Details can be found in the Javadoc of that method.
This method will rarely fail to traverse any valid squares.  However, it can happen.  An optimization could be made to handle this rare edge case.

Javadoc won't be created for inner classes, so detailed reference will have to be done by reading the comments in the source code.

When the scanning ant completes scanning the map, it becomes a worker ant on the spot.

------------
Future Improvements:
Support variable number of Scanners, with different preferred routes for bigger maps.
Continue to optimize when scanners choose to return home.  Abstract out more logic.
Make search radius threshold parameters easier to modify.
Leave the unused portion of the grid null, rather than default to a value.
