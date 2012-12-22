package search.main;

import java.util.List;

import org.transdroid.daemon.DaemonMethod;
import org.transdroid.daemon.IDaemonAdapter;
import org.transdroid.daemon.Label;
import org.transdroid.daemon.Torrent;
import org.transdroid.daemon.Transmission.TransmissionAdapter;
import org.transdroid.daemon.task.AddByMagnetUrlTask;
import org.transdroid.daemon.task.DaemonTask;
import org.transdroid.daemon.task.DaemonTaskResult;
import org.transdroid.daemon.task.ResumeTask;
import org.transdroid.daemon.task.RetrieveTask;
import org.transdroid.daemon.task.RetrieveTaskSuccessResult;

import android.os.Bundle;

public class main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		TransmissionInterface trans = new TransmissionInterface();
		IDaemonAdapter client = trans.TransmissionInterface();
		
		System.out.println(client.getSettings().toString());
		
		/*
		DaemonMethod method = DaemonMethod.PauseAll;
		DaemonTask task = new DaemonTask(client, method, null, null);
		DaemonTaskResult taskResult = task.execute();
		System.out.println(taskResult.wasSuccessful());
		System.out.println(taskResult.toString());
		*/
		
		
		DaemonMethod method = DaemonMethod.AddByMagnetUrl;
		Bundle bundle = new Bundle();
		String url = "magnet:?xt=urn:btih:ba35b728937aad75bf9e8f0713fecad43059e5b4&dn=Scientific+American+Magazine+Full+Year+Collection+2012+%2812+Issue&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.istole.it%3A6969&tr=udp%3A%2F%2Ftracker.ccc.de%3A80";
		bundle.putString("URL", url);
		
		//Torrent torrent = new Torrent(0, null, null, null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null);
		//DaemonTask task = new DaemonTask(client, method, torrent, null);
		AddByMagnetUrlTask task = new AddByMagnetUrlTask(client, bundle);
		DaemonTaskResult taskResult = task.execute();
		System.out.println(taskResult.wasSuccessful());
		System.out.println(taskResult.toString());
		
		RetrieveTask retrieveTask = new RetrieveTask(client);
		RetrieveTaskSuccessResult retrieveTaskResult = (RetrieveTaskSuccessResult) retrieveTask.execute();
		List<Torrent> retrievedTorrents = retrieveTaskResult.getTorrents();
		//List<Label> labels = retrieveTaskResult.getLabels();
		
		for(Torrent t : retrievedTorrents){
			System.out.println(t.getName());
			System.out.println(t.getDownloadedPercentage());
			System.out.println(t.getTotalSize());
			System.out.println(t.getUniqueID());
			ResumeTask resumeTask = ResumeTask.create(client, t);
			resumeTask.execute();
			
		}
	}

}
