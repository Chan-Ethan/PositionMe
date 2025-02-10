package com.openpositioning.PositionMe;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReplayFragment extends Fragment implements OnMapReadyCallback {

    private boolean isPlaying = false;
    private int progress = 0;
    private Handler refreshDataHandler;
    private Traj.Trajectory receTraj;
    private int PdrNum;
    private int GnssNum;
    private GoogleMap gMap;
    private Polyline polyline;
    private Marker positionMarker;
    private SeekBar seekBar;
    private List<LatLng> pdrCoordinates = new ArrayList<>();
    private List<LatLng> gnssCoordinates = new ArrayList<>();
    private long totalDuration = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readTrajectoryData(); // Load trajectory data on fragment creation
        refreshDataHandler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_replay, container, false);

        // Initialize map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.replayMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Initialize UI components
        ImageButton playPauseButton = view.findViewById(R.id.btn_play_pause);
        Button restartButton = view.findViewById(R.id.btn_restart);
        Button goToEndButton = view.findViewById(R.id.btn_go_to_end);
        Button exitButton = view.findViewById(R.id.btn_exit);
        seekBar = view.findViewById(R.id.seek_bar);
        Switch gnssSwitch = view.findViewById(R.id.gnssSwitch);

        // Play/Pause button logic
        playPauseButton.setOnClickListener(v -> {
            isPlaying = !isPlaying;
            if (isPlaying) {
                playPauseButton.setImageResource(R.drawable.ic_baseline_pause_circle_filled_24);
                startPlayback();
            } else {
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_circle_filled_24_b);
                pausePlayback();
            }
        });

        // Restart button: reset progress to 0
        restartButton.setOnClickListener(v -> {
            progress = 0;
            seekBar.setProgress(0);
            updateUIandPosition(0); // Update to initial position
        });

        // Exit button: return to previous fragment
        exitButton.setOnClickListener(v -> requireActivity().onBackPressed());

        // GNSS switch: toggle GNSS markers visibility (implementation needed)
        gnssSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Handle GNSS marker visibility
        });

        // SeekBar listener for manual progress updates
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    ReplayFragment.this.progress = progress;
                    updateUIandPosition(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        return view;
    }

    // Initialize map and set up polyline/marker
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        gMap.getUiSettings().setZoomControlsEnabled(true);

        if (!pdrCoordinates.isEmpty()) {
            // Set initial position to first PDR point
            LatLng initialPos = pdrCoordinates.get(0);
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 19f));
            positionMarker = gMap.addMarker(new MarkerOptions()
                    .position(initialPos)
                    .title("Replay Position"));
            
            // Initialize polyline with blue color
            PolylineOptions options = new PolylineOptions()
                    .color(Color.BLUE)
                    .width(8f);
            polyline = gMap.addPolyline(options);
        }
    }

    // Read trajectory data from file (modified to use absolute coordinates)
    private void readTrajectoryData() {
        File file = new File(requireContext().getFilesDir(), "received_trajectory.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            if (file.exists()) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) content.append(line);

                // Parse Protobuf data
                receTraj = Traj.Trajectory.parseFrom(content.toString().getBytes());
                PdrNum = receTraj.getPdrCount();
                GnssNum = receTraj.getGnssCount();

                // Extract PDR coordinates (assuming absolute lat/lng)
                for (int i = 0; i < PdrNum; i++) {
                    Traj.PDRData pdr = receTraj.getPdrData(i);
                    pdrCoordinates.add(new LatLng(pdr.getLatitude(), pdr.getLongitude()));
                }

                // Calculate total duration based on last PDR timestamp
                if (PdrNum > 0) {
                    totalDuration = receTraj.getPdrData(PdrNum - 1).getRelativeTimestamp();
                    seekBar.setMax((int) totalDuration);
                }
            }
        } catch (IOException e) {
            Log.e("ReplayFragment", "Error reading trajectory", e);
        }
    }

    // Start playback: update progress periodically
    private void startPlayback() {
        refreshDataHandler.postDelayed(new Runnable() { 
            @Override
            public void run() {
                if (isPlaying && progress < totalDuration) {
                    progress += 200; // Increment by 200ms
                    seekBar.setProgress(progress);
                    updateUIandPosition(progress);
                    refreshDataHandler.postDelayed(this, 200); // Update every 200ms
                }
            }
        }, 100);
    }

    // Pause playback
    private void pausePlayback() {
        refreshDataHandler.removeCallbacksAndMessages(null);
    }

    // Update map based on current progress
    private void updateUIandPosition(int progress) {
        if (pdrCoordinates.isEmpty() || polyline == null) return;

        // Find the closest PDR point based on timestamp
        int index = findClosestPdrIndex(progress);
        LatLng currentPos = pdrCoordinates.get(index);

        // Update polyline path
        List<LatLng> points = new ArrayList<>(pdrCoordinates.subList(0, index + 1));
        polyline.setPoints(points);

        // Update marker position and camera
        if (positionMarker != null) {
            positionMarker.setPosition(currentPos);
            gMap.moveCamera(CameraUpdateFactory.newLatLng(currentPos));
        }
    }

    // Binary search to find closest PDR index by timestamp
    private int findClosestPdrIndex(long timestamp) {
        int low = 0, high = PdrNum - 1;
        while (low < high) {
            int mid = (low + high) / 2;
            long midTime = receTraj.getPdrData(mid).getRelativeTimestamp();
            if (midTime < timestamp) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}