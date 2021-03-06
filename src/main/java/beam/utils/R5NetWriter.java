package beam.utils;

import com.conveyal.r5.streets.EdgeStore;
import com.conveyal.r5.transit.TransportNetwork;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by Andrew A. Campbell on 9/21/17.
 */
public class R5NetWriter {

	private TransportNetwork transportNetwork;
	private String outPath;

	public R5NetWriter(String netPath, String outPath){
		File netFile = new File(netPath);
		this.outPath = outPath;
		try {
			this.transportNetwork = TransportNetwork.read(netFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Dumps the R5 network to a unicode text file.
	 */
	public void dumpNetwork() throws IOException {
		EdgeStore.Edge cursor = this.transportNetwork.streetLayer.edgeStore.getCursor();
		FileWriter writer = new FileWriter(this.outPath);
		while (cursor.advance()){
			String line = new String();
			line += String.valueOf(cursor.getEdgeIndex()) + ",";
			line += "[";
			for (EdgeStore.EdgeFlag flag: cursor.getFlags()){
				line += flag.name() + " ";
			}
			line += "]\n";
			writer.write(line);
		}
		writer.flush();
		writer.close();
	}

	/**
	 *
	 * @param args 0) Path to networ.dat, 1) Output csv path
	 */
	public static void main(String[] args) throws IOException {
		R5NetWriter r5NW = new R5NetWriter(args[0], args[1]);
		r5NW.dumpNetwork();
	}
}
