package search.main;

import org.transdroid.daemon.Daemon;
import org.transdroid.daemon.DaemonSettings;
import org.transdroid.daemon.IDaemonAdapter;
import org.transdroid.daemon.OS;
import org.transdroid.daemon.Transmission.TransmissionAdapter;

public class TransmissionInterface {
	
	IDaemonAdapter TransmissionInterface(){

		
		String name = "Transmission";
		Daemon type = Daemon.Transmission;
		String address = "localhost";
		int port = 9091;
		boolean ssl = false;
		boolean sslTrustAll = false;
		String sslTrustKey = null;
		String folder = null;
		boolean useAuthentication = false;
		String username = null;
		String password = null;
		String extraPass = null;
		OS os = OS.Windows;
		String downloadDir = "C:\\Users\\Manuel Mantovani\\Download\\torrent\\";
		String ftpUrl = null;
		String ftpPassword = null;
		int timeout = 30;
		boolean alarmOnFinishedDownload = false;
		boolean alarmOnNewTorrent = false;
		String idString = "1";
		boolean isAutoGenerated= false;
		
		DaemonSettings settings = new DaemonSettings(name, type, address, port, ssl, sslTrustAll, sslTrustKey, folder, useAuthentication, 
                username, password, extraPass, os, downloadDir, ftpUrl, ftpPassword, timeout, alarmOnFinishedDownload, alarmOnNewTorrent, idString, isAutoGenerated);
	
		//TransmissionAdapter client = new TransmissionAdapter(settings);
		IDaemonAdapter client = new TransmissionAdapter(settings);
	
		
		System.out.println("Sono arrivato qui\n");
		System.out.println(client.toString());
		System.out.println("\nSono arrivato anche qui\n");
		return client;
	}
	
}
