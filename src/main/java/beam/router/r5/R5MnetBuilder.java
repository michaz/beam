package beam.router.r5;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.conveyal.r5.streets.EdgeStore;
import com.conveyal.r5.transit.TransportNetwork;
import com.vividsolutions.jts.geom.Coordinate;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Build the pruned R5 network and MATSim network. These two networks have 1-1 link parity.
 * The R5 edgeIndex values match the MATSim link IDs w/ 1-1 parity.
 *
 * Created by Andrew A. Campbell on 6/7/17.
 */
public class R5MnetBuilder {
	private static final Logger log = Logger.getLogger(R5MnetBuilder.class);

	private final TransportNetwork r5Network;
	private Network mNetowrk = null;  // MATSim mNetowrk
	//TODO - the CRS should be settable not hard coded
	private String fromCRS = "EPSG:4326";  // WGS84
	private String toCRS = "EPSG:26910";  // UTM10N
	private GeotoolsTransformation transform = new GeotoolsTransformation(this.fromCRS, this.toCRS);
	private String osmFile;

	private HashMap<Coord, Id<Node>> nodeMap = new HashMap<>();  // Maps x,y Coord to node ID
	private int nodeId = 0;  // Current new MATSim network Node ids
	private EnumSet<EdgeStore.EdgeFlag> modeFlags = EnumSet.noneOf(EdgeStore.EdgeFlag.class); // modes to keep

	private Long lastProcessedOSMId;
	private Set<Integer> lastProcessedNodes = new HashSet<>(2);

	/**
	 *
	 * @param r5Net R5 network.
	 * @param osmDBPath Path to mapdb file with OSM data
	 * @param modeFlags EnumSet defining the modes to be included in the network. See
	 *                     com.conveyal.r5.streets.EdgeStore.EdgeFlag for EdgeFlag definitions.
	 */
	public R5MnetBuilder(TransportNetwork r5Net, String osmDBPath, EnumSet<EdgeStore.EdgeFlag> modeFlags){
		this.osmFile = osmDBPath;
		log.info("Found R5 Transport Network file, loading....");
		this.r5Network = r5Net;
		this.modeFlags = modeFlags;
		this.mNetowrk = NetworkUtils.createNetwork();
	}

	public void buildMNet() {
		// Load the OSM file for retrieving the number of lanes, which is not stored in the R5 network
		OSM osm = new OSM(this.osmFile);
		Map<Long, Way> ways = osm.ways;
		EdgeStore.Edge cursor = r5Network.streetLayer.edgeStore.getCursor();  // Iterator of edges in R5 network
		OsmToMATSim OTM = new OsmToMATSim(this.mNetowrk, this.transform, true);
		ArrayList<Long> newMLinkIDs = new ArrayList<>();
		while (cursor.advance()) {
			// TODO - eventually, we should pass each R5 link to OsmToMATSim and do the two-way handling there.
			// Check if we have already seen this OSM way. Skip if we have.
			Integer edgeIndex = cursor.getEdgeIndex();
			if (newMLinkIDs.size() == 2){
				if (((edgeIndex-1)%2) == 1){  // Previous was an odd edge. Hopefully this never happens.
					System.out.println("WHAT NOW?");
					break;
				}
				newMLinkIDs = new ArrayList<>();
				continue;
			} else if (newMLinkIDs.size() == 1){
			} else if (newMLinkIDs.size() > 2){
				System.out.print("WHAT NOW AGAIN?");
				break;
			}
			Long osmID = cursor.getOSMID();  // id of edge in the OSM db
			Way way = ways.get(osmID);
			Set<Integer> deezNodes = new HashSet<>(2);
			deezNodes.add(cursor.getFromVertex());
			deezNodes.add(cursor.getToVertex());
			if (osmID.equals(this.lastProcessedOSMId) && deezNodes.equals(this.lastProcessedNodes)) {
				log.info("EDGE SKIPPED - already processed edge." + "From: " +
						String.valueOf(cursor.getFromVertex()) + " To: " +
						String.valueOf(cursor.getToVertex()) +  " OSM ID: " + String.valueOf(osmID));
				newMLinkIDs = new ArrayList<>();
				continue;
			}
			// Check if this edge permits any of the desired modes.
			EnumSet<EdgeStore.EdgeFlag> flags = cursor.getFlags();
			flags.retainAll(this.modeFlags);
			if (flags.isEmpty()) {
				log.info("EDGE SKIPPED - no allowable modes: " + "cursor: " + String.valueOf(cursor.getEdgeIndex()) +
				 "OSM ID: " + String.valueOf(osmID));
				newMLinkIDs = new ArrayList<>();
				continue;
			}
			// Convert flags to strings that the MATSim network will recognize.
			HashSet<String> flagStrings = new HashSet<>();
			for (EdgeStore.EdgeFlag eF : flags) {
				flagStrings.add(flagToString(eF));
			}
			////
			//Add the edge and its nodes
			////
//			int lanes = Integer.valueOf(way.getTag("lanes"));
//			Integer idx = cursor.getEdgeIndex();
			//TODO - length needs to be transformed to output CRS. It is important that we calculate the length here
			//TODO - because R5 often breaks single OSM links into pieces.
			double length = cursor.getLengthM();  // R5 link length in meters, as calculated by R5
//			double speed = cursor.getSpeedMs();
			// Get start and end coordinates for the edge
			Coordinate tempFromCoord = cursor.getGeometry().getCoordinate();
			Coord fromCoord = transformCRS(new Coord(tempFromCoord.x, tempFromCoord.y));  // MATSim coord
			Coordinate[] tempCoords = cursor.getGeometry().getCoordinates();
			Coordinate tempToCoord = tempCoords[tempCoords.length - 1];
			Coord toCoord = transformCRS(new Coord(tempToCoord.x, tempToCoord.y));
			// Add R5 start and end nodes to the MATSim network
			// Grab existing nodes from mNetwork if they already exist, else make new ones and add to mNetwork
			Node fromNode = this.getOrMakeNode(fromCoord);
			Node toNode = this.getOrMakeNode(toCoord);
			// Make and add the link (only if way exists)

			if (way != null) {
//				System.out.println(way.getTag("highway"));
				newMLinkIDs = OTM.createLink(way, osmID, edgeIndex, fromNode, toNode, length, flagStrings);
				this.lastProcessedOSMId = osmID;
				this.lastProcessedNodes = deezNodes;
			} else {
				newMLinkIDs = new ArrayList<>(); // reset the IDs
			}
		}
	}

	public void writeMNet(String mNetPath){
		NetworkWriter nw = new NetworkWriter(this.mNetowrk);
		nw.write(mNetPath);
	}


	/**
	 * Checks whether we already have a MATSim Node at the Coord. If so, returns that Node. If not, makes and adds
	 * a new Node to the network.
	 * @param coord
	 * @return
	 */
	private Node getOrMakeNode(Coord coord){
		Node dummyNode;
		Id<Node> id;
		if (this.nodeMap.containsKey(coord)){  // node already exists.
//			log.info("NODE ALREADY EXISTS");
			id = this.nodeMap.get(coord);
			dummyNode = this.mNetowrk.getNodes().get(id);
		} else { // need to make new fromID and node and increment the nodeId
			id = Id.createNodeId(this.nodeId);
			this.nodeId++;
			dummyNode = NetworkUtils.createAndAddNode(mNetowrk, id, coord);
			this.nodeMap.put(coord, id);
		}
		return dummyNode;
	}

	/*
	Tranforms from WGS84 to UTM 26910
	/TODO - this helper is not needed now that we have this.transform. But setTransform() needs to be updated
	 */
	private Coord transformCRS(Coord coord){
		return transform.transform(coord);
	}

	/**
	 * Returns the corresponding mode string for the EdgeFlag. See com.conveyal.r5.streets.EdgeStore.EdgeFlag and
	 * org.matsim.api.core.v01.TransportMode for definitions.
	 * @param flag EdgeFlag describing link travel modes.
	 * @return
	 */
	//TODO - we should probably make the cases settable for the case that we want to use custom modes in the MATSim net
	private String flagToString(EdgeStore.EdgeFlag flag){
		String out = null;
		switch (flag){
			case ALLOWS_PEDESTRIAN:
				out = "walk";
				break;
			case ALLOWS_BIKE:
				out = "bike";
				break;
			case ALLOWS_CAR:
				out = "car";
				break;
		}
		try {
			return out;
		}
		catch (NullPointerException exec) {
			log.error("No matching flag");
			exec.printStackTrace();
		}
		return out;
	}

}

