package org.matsim.contrib.josm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.josm.scenario.EditableScenarioUtils;
import org.matsim.contrib.osm.CreateStopAreas;
import org.matsim.contrib.osm.IncompleteRoutesTest;
import org.matsim.contrib.osm.MasterRoutesTest;
import org.matsim.contrib.osm.UpdateStopTags;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;


public class CreateTransitLiteRoute {



	public static void main (String[] args) throws IllegalDataException, IOException {

		//adjust fileName for custom data conversion
		String fileName = "/test-input/OSMData/busRoute.osm.xml";
		File file = new File(fileName);
		new JOSMFixture(file.getParent()).init(true);
		System.out.println("Fixture initialized");

		System.out.println("Reading DataSet");
		InputStream stream = Compression.getUncompressedFileInputStream(file);
		DataSet set = OsmReader.parseDataSet(stream, null);
		System.out.println("DataSet ready");

		OsmDataLayer layer = new OsmDataLayer(set, "tmp", file);

		Main.pref.put("matsim_supportTransit", true);
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		NetworkListener listener = new NetworkListener(set, EditableScenarioUtils.createScenario(config), new HashMap<Way, List<Link>>(),
				new HashMap<Link, List<WaySegment>>(), new HashMap<Relation, TransitStopFacility>());
		System.out.println("Listener set");


		Main.pref.addPreferenceChangeListener(listener);
		listener.visitAll();
		set.addDataSetListener(listener);
		Main.main.addLayer(layer);
		System.out.println("Layer added");

		System.out.println("Starting Validations");
		List<Test> tests = Arrays.asList(new IncompleteRoutesTest(), new UpdateStopTags(), new MasterRoutesTest(),new CreateStopAreas());
		for (Test test: tests) {
			System.out.println("Starting "+test.getName());

			test.startTest(null);
			test.visit(layer.data.allPrimitives());
			test.endTest();
			System.out.println(test.getErrors().size()+" errors found");
			int i=0;
			for(TestError error: test.getErrors()) {
				if (error.isFixable()) {
					final Command fixCommand = error.getFix();
					if (fixCommand != null) {
						if(i%50==0) {
							System.out.println("Fixing error #"+i);
						}
						fixCommand.executeCommand();
						i++;
					}
				}
			}
		}

		System.out.println("Converting data");
		LayerConverter converter =  new LayerConverter(layer);
		converter.run();
		Main.main.addLayer(converter.getMatsimLayer());
		System.out.println("Exporting data");

		TransitScheduleTest test = new TransitScheduleTest();
		PleaseWaitProgressMonitor progMonitor = new
				PleaseWaitProgressMonitor("Validation");
		// run validator tests
		test.startTest(progMonitor);
		test.visit(Main.main.getCurrentDataSet().allPrimitives());
		test.endTest();
		progMonitor.finishTask();
		progMonitor.close();

		for(TestError error: test.getErrors()) {
			if (error.isFixable()) {
				final Command fixCommand = error.getFix();
				if (fixCommand != null) {
					fixCommand.executeCommand();
				}
			}
		}

		TransitScheduleExportAction action = new TransitScheduleExportAction();
		action.actionPerformed(null);
	}
}