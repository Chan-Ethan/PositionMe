package com.openpositioning.PositionMe.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.ServerCommunications;
import com.openpositioning.PositionMe.sensors.Observer;
import com.openpositioning.PositionMe.viewitems.TrajDownloadListAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass. The files fragments displays a list of trajectories already
 * uploaded with some metadata, and enabled re-downloading them to the device's local storage.
 *
 * @see HomeFragment the connected fragment in the nav graph.
 * @see UploadFragment sub-menu for uploading recordings that failed during recording.
 * @see com.openpositioning.PositionMe.Traj the data structure sent and received.
 * @see com.openpositioning.PositionMe.ServerCommunications the class handling communication with the server.
 *
 * @author Mate Stodulka
 */
public class FilesFragment extends Fragment implements Observer {

    // UI elements
    private RecyclerView filesList;
    private TrajDownloadListAdapter listAdapter;
    private CardView uploadCard;
    private Button REPLAY;
    
    // Class handling HTTP communication
    private ServerCommunications serverCommunications;
    
    // Download progress tracking
    private boolean downloadInProgress = false;
    private Dialog progressDialog;

    /**
     * Default public constructor, empty.
     */
    public FilesFragment() {
        // Required empty public constructor
    }

    /**
     * {@inheritDoc}
     * Initialise the server communication class and register the FilesFragment as an Observer to
     * receive the async http responses.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        serverCommunications = new ServerCommunications(getActivity());
        serverCommunications.registerObserver(this);
    }

    /**
     * {@inheritDoc}
     * Sets the title in the action bar.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_files, container, false);
        getActivity().setTitle("Trajectory recordings");
        return rootView;
    }

    /**
     * {@inheritDoc}
     * Called by {@link ServerCommunications} when the response to the HTTP info request is received.
     *
     * @param singletonStringList   a single string wrapped in an object array containing the http
     *                              response from the server.
     */
    @Override
    public void update(Object[] singletonStringList) {
        // Cast input as a string
        String infoString = (String) singletonStringList[0];
        // Check if the string is non-null and non-empty before processing
        if(infoString != null && !infoString.isEmpty()) {
            // Start a handler to be able to modify UI elements
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    // Check if this is a progress update
                    if (infoString.startsWith("DOWNLOAD_PROGRESS:")) {
                        try {
                            // Extract progress percentage
                            int progress = Integer.parseInt(infoString.split(":")[1]);
                            // Update progress in dialog
                            updateProgressDialog(progress);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    // check if the download is complete
                    else if (infoString.equals("DOWNLOAD_COMPLETE")) {
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        showDownloadCompleteDialog();
                        downloadInProgress = false;
                    } else if (infoString.equals("DOWNLOAD_FAILED")) {
                        // handle the download failed case
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        showDownloadFailedDialog();
                        downloadInProgress = false;
                    } else if (infoString.equals("DOWNLOAD_CANCELED")) {
                        // User has already canceled the download, no need to show dialog
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        downloadInProgress = false;
                    } else {
                        // handle the normal server info response
                        List<Map<String, String>> entryList = processInfoResponse(infoString);
                        updateView(entryList);
                    }
                }
            });
        }
    }

    /**
     * Updates the progress dialog with the current download progress
     * 
     * @param progress download progress percentage (0-100)
     */
    private void updateProgressDialog(int progress) {
        if (progressDialog != null && progressDialog.isShowing()) {
            android.widget.TextView progressMessage = progressDialog.findViewById(R.id.progressMessage);
            if (progressMessage != null) {
                progressMessage.setText(getString(R.string.downloading) + " " + progress + "%");
            }
        }
    }

    /**
     * Shows a dialog indicating the download has completed
     */
    private void showDownloadCompleteDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("File downloaded")
                .setMessage("Trajectory downloaded to local storage")
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.show_storage, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                    }
                })
                .setIcon(R.drawable.ic_baseline_download_24)
                .show();
    }

    /**
     * Shows a progress dialog for the download
     */
    private void showDownloadProgressDialog() {
        progressDialog = new Dialog(getContext());
        progressDialog.setContentView(R.layout.progress_dialog);
        progressDialog.setCancelable(true);
        
        // Initialize with 0% progress
        android.widget.TextView progressMessage = progressDialog.findViewById(R.id.progressMessage);
        if (progressMessage != null) {
            progressMessage.setText(getString(R.string.downloading) + " 0%");
        }
        
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // Cancel download flag
                downloadInProgress = false;
                // Cancel background download operation
                serverCommunications.cancelDownload();
            }
        });
        
        progressDialog.show();
    }

    /**
     * Shows a dialog indicating the download has failed
     */
    private void showDownloadFailedDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Download Failed")
                .setMessage("Failed to download trajectory. Please try again.")
                .setPositiveButton(R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Parses the info response string from the HTTP communication.
     * Process the data using the Json library and return the matching Java data structure as a
     * List of Maps of \<String, String\>. Throws a JSONException if the data is not valid.
     *
     * @param infoString    HTTP info request response as a single string
     * @return              List of Maps of String to String containing ID, owner ID, and date.
     */
    private List<Map<String, String>> processInfoResponse(String infoString) {
        // Initialise empty list
        List<Map<String, String>> entryList = new ArrayList<>();
        try {
            // Attempt to decode using known JSON pattern
            JSONArray jsonArray = new JSONArray(infoString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject trajectoryEntry = jsonArray.getJSONObject(i);
                Map<String, String> entryMap = new HashMap<>();
                entryMap.put("owner_id", String.valueOf(trajectoryEntry.get("owner_id")));
                entryMap.put("date_submitted", (String) trajectoryEntry.get("date_submitted"));
                entryMap.put("id", String.valueOf(trajectoryEntry.get("id")));
                // Add decoded map to list of entries
                entryList.add(entryMap);
            }
        } catch (JSONException e) {
            System.err.println("JSON reading failed");
            e.printStackTrace();
        }
        // Sort the list by the ID fields of the maps
        entryList.sort(Comparator.comparing(m -> Integer.parseInt(m.get("id")), Comparator.nullsLast(Comparator.naturalOrder())));
        return entryList;
    }

    /**
     * Update the RecyclerView in the FilesFragment with new data.
     * Must be called from a UI thread. Initialises a new Layout Manager, and passes it to the
     * RecyclerView. Initialises a {@link TrajDownloadListAdapter} with the input array and setting
     * up a listener so that trajectories are downloaded when clicked, and a pop-up message is
     * displayed to notify the user.
     *
     * @param entryList List of Maps of String to String containing metadata about the uploaded
     *                  trajectories (ID, owner ID, date).
     */
    private void updateView(List<Map<String, String>> entryList) {
        // Initialise RecyclerView with Manager and Adapter
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        filesList.setLayoutManager(manager);
        filesList.setHasFixedSize(true);
        listAdapter = new TrajDownloadListAdapter(getActivity(), entryList, position -> {
            // Show loading dialog
            showDownloadProgressDialog();
            // Set download in progress flag
            downloadInProgress = true;
            // Download the appropriate trajectory instance
            serverCommunications.downloadTrajectory(position);
        });
        filesList.setAdapter(listAdapter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get RecyclerView
        filesList = view.findViewById(R.id.filesList);

        // Get upload button
        uploadCard = view.findViewById(R.id.uploadCard);
        uploadCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavDirections action = FilesFragmentDirections.actionFilesFragmentToUploadFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        // Get and initialize REPLAY button
        REPLAY = view.findViewById(R.id.replayButton); // Make sure there is this button in fragment_files.xml
        REPLAY.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavDirections action = FilesFragmentDirections.actionFilesFragmentToReplayFragment();
                Navigation.findNavController(view).navigate(action);
            }
        });

        // Request server data
        serverCommunications.sendInfoRequest();
    }
}