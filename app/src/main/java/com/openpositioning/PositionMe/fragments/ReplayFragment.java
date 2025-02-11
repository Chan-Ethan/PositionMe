package com.openpositioning.PositionMe.fragments;
import com.openpositioning.PositionMe.Traj;
import com.openpositioning.PositionMe.R;

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
import android.widget.TextView;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

public class ReplayFragment extends Fragment implements OnMapReadyCallback {

    private boolean isPlaying = false;
    private int progress = 0;
    private static final int SEEK_TIME = 10000; // 快进/快退步长（毫秒）
    private Handler refreshDataHandler;
    private Traj.Trajectory receTraj;
    private int PdrNum;
    private int GnssNum;
    private GoogleMap gMap;
    private Polyline polyline;
    private Marker positionMarker;
    private SeekBar seekBar;
    // private List<LatLng> pdrCoordinates = new ArrayList<>();
    // private List<LatLng> gnssCoordinates = new ArrayList<>();
    private int totalDuration = 0;
    private int MaxProgress;
    private TextView tvProgressTime; // 新增时间显示控件

    private float pdrX, pdrY;   // current progress PDR data
    private float gnssLati, gnssLong; // current progress GNSS data
    private float elevation;    // current progress elevation
    private int pdrIndex = 0;       // current progress PDR index
    private int gnssIndex;      // current progress GNSS index

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshDataHandler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_replay, container, false);
        requireActivity().setTitle("Replay"); // 设置标题

        // 初始化地图
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.replayMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 初始化控件
        ImageButton playPauseButton = view.findViewById(R.id.btn_play_pause);
        ImageButton rewindButton = view.findViewById(R.id.btn_rewind);
        ImageButton forwardButton = view.findViewById(R.id.btn_forward);
        Button restartButton = view.findViewById(R.id.btn_restart);
        Button goToEndButton = view.findViewById(R.id.btn_go_to_end);
        Button exitButton = view.findViewById(R.id.btn_exit);
        seekBar = view.findViewById(R.id.seek_bar);
        Switch gnssSwitch = view.findViewById(R.id.gnssSwitch);
        tvProgressTime = view.findViewById(R.id.tv_progress_time); // 绑定时间显示控件

        // 播放/暂停按钮
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

        // 快退 10 秒
        rewindButton.setOnClickListener(v -> {
            int newProgress = Math.max(progress - SEEK_TIME, 0);
            seekBar.setProgress(newProgress);
            updateUIandPosition(newProgress);
            Toast.makeText(getContext(), "Rewind 10 seconds", Toast.LENGTH_SHORT).show();
        });

        // 快进 10 秒
        forwardButton.setOnClickListener(v -> {
            int newProgress = Math.min(progress + SEEK_TIME, seekBar.getMax());
            seekBar.setProgress(newProgress);
            updateUIandPosition(newProgress);
            Toast.makeText(getContext(), "Forward 10 seconds", Toast.LENGTH_SHORT).show();
        });

        // 重启按钮
        restartButton.setOnClickListener(v -> {
            progress = 0;
            seekBar.setProgress(0);
            updateUIandPosition(0);
            Toast.makeText(getContext(), "Restart button clicked", Toast.LENGTH_SHORT).show();
        });

        // 跳转到结尾
        goToEndButton.setOnClickListener(v -> {
            progress = seekBar.getMax();
            seekBar.setProgress(progress);
            updateUIandPosition(progress);
            Toast.makeText(getContext(), "Go to End button clicked", Toast.LENGTH_SHORT).show();
        });

        // 退出按钮
        exitButton.setOnClickListener(v -> {
            requireActivity().onBackPressed();
            Toast.makeText(getContext(), "Exit button clicked", Toast.LENGTH_SHORT).show();
        });

        // GNSS 开关
        gnssSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(getContext(), "GNSS Enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "GNSS Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // SeekBar 监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    ReplayFragment.this.progress = progress;
                    updateUIandPosition(progress);
                    updateTimeDisplay(progress); // 更新时间显示
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getContext(), "SeekBar dragged", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getContext(), "SeekBar released", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        readTrajectoryData();
    }

    // 地图初始化
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d("ReplayFragment", "onMapReady");
        gMap = googleMap;
        gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        gMap.getUiSettings().setCompassEnabled(true);
        gMap.getUiSettings().setTiltGesturesEnabled(true);
        gMap.getUiSettings().setRotateGesturesEnabled(true);
        gMap.getUiSettings().setScrollGesturesEnabled(true);
        gMap.getUiSettings().setZoomControlsEnabled(true);

        PolylineOptions options = new PolylineOptions().color(Color.RED).width(8f);
        polyline = gMap.addPolyline(options);
        Log.d("ReplayFragment", "polyline added");

        if ((pdrX != 0) || (pdrY != 0)) {
            LatLng initialPdrPos = new LatLng(pdrX, pdrY);
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPdrPos, 19f));
            // positionMarker = gMap.addMarker(new MarkerOptions().position(initialPos).title("Position"));
        }
        else {
            Log.e("ReplayFragment", "No PDR data to replay");
            Toast.makeText(getContext(), "No PDR data to replay", Toast.LENGTH_LONG).show();
        }
    }
    
    private void readTrajectoryData() {
        try {
            // Get file path and log details
            File file = new File(requireContext().getFilesDir(), "received_trajectory.traj");
            // Log.d("ReplayFragment", "File path: " + file.getAbsolutePath());
            // Log.d("ReplayFragment", "File exists: " + file.exists());
            // byte[] Bytes = readFileToByteArray(file);
            FileInputStream fileStream = new FileInputStream(file);
            
            // receTraj = Traj.Trajectory.parseFrom(Bytes);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            // Read the zipped data and write it to the byte array output stream
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            // Convert the byte array to a protobuf object
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            System.out.println("ReplayFragment File load, size is: " + byteArray.length + " bytes");
            receTraj = Traj.Trajectory.parseFrom(byteArray);

            // Extract trajectory details
            PdrNum = receTraj.getPdrDataCount();
            GnssNum = receTraj.getGnssDataCount();
            Log.d("ReplayFragment", "Trajectory parsed successfully. GNSS points: " + GnssNum);
            Log.d("ReplayFragment", "Trajectory parsed successfully. PDR points: " + PdrNum);
            Log.d("ReplayFragment", "Start Timestamp: " + receTraj.getStartTimestamp());

            // if no PDR record, stop
            if (PdrNum == 0) {
                Log.w("ReplayFragment", "No PDR data to replay");
                return;
            }

            // Calculate total duration
            if (receTraj.getPdrData(PdrNum-1).getRelativeTimestamp() > Integer.MAX_VALUE) {
                MaxProgress = Integer.MAX_VALUE;
                Log.w("ReplayFragment", "Trajectory too long, playback limited to 2^31-1 milliseconds");
            }
            else {
                MaxProgress = (int)receTraj.getPdrData(PdrNum-1).getRelativeTimestamp();
                Log.d("ReplayFragment", "MaxProgress = "+MaxProgress);
            }
            seekBar.setMax(MaxProgress);
            // totalDuration = MaxProgress - receTraj.getStartTimestamp();

            // initial current progress data
            pdrX = receTraj.getPdrData(0).getX();
            pdrY = receTraj.getPdrData(0).getY();
            Log.d("ReplayFragment", "pdrX = "+pdrX);
            Log.d("ReplayFragment", "pdrY = "+pdrY);
            if (GnssNum > 0) {
                gnssLati = receTraj.getGnssData(0).getLatitude();
                gnssLong = receTraj.getGnssData(0).getLongitude();
            }
            else {
                gnssLati = 0;
                gnssLong = 0;
            }

            // Calculate elevation


        } catch (IOException | JsonSyntaxException e) {
            Log.e("ReplayFragment", "Failed to read trajectory", e);
            Toast.makeText(getContext(), "Error: Invalid trajectory file", Toast.LENGTH_LONG).show();
        }
    }

    // 开始播放
    private void startPlayback() {
        refreshDataHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying && progress < MaxProgress) {
                    progress = (int)Math.min(progress+200, MaxProgress);
                    Log.d("ReplayFragment", "current Progress = "+progress);
                    seekBar.setProgress(progress);
                    updateUIandPosition(progress);
                    updateTimeDisplay(progress); // 新增时间更新
                    refreshDataHandler.postDelayed(this, 200);
                }
            }
        }, 100);
        Toast.makeText(getContext(), "Playback started", Toast.LENGTH_SHORT).show();
    }

    // 暂停播放
    private void pausePlayback() {
        refreshDataHandler.removeCallbacksAndMessages(null);
        Toast.makeText(getContext(), "Playback paused", Toast.LENGTH_SHORT).show();
    }

    static List<LatLng> points = new ArrayList<>();
    // 更新地图和UI
    private void updateUIandPosition(int progress) {
        pdrIndex = findClosestPdrIndex(progress, pdrIndex);
        Log.d("ReplayFragment", "X = "+receTraj.getPdrData(pdrIndex).getX());
        Log.d("ReplayFragment", "Y = "+receTraj.getPdrData(pdrIndex).getY());
        LatLng currentPos = new LatLng(receTraj.getPdrData(pdrIndex).getX(),
                receTraj.getPdrData(pdrIndex).getY());

        points.add(currentPos);
        polyline.setPoints(points);

        if (positionMarker != null) {
            positionMarker.setPosition(currentPos);
            gMap.moveCamera(CameraUpdateFactory.newLatLng(currentPos));
        }
    }

    // 时间显示格式化
    private void updateTimeDisplay(int progress) {
        int seconds = (progress / 1000) % 60;
        int minutes = (progress / 1000) / 60;
        String time = String.format("%d:%02d", minutes, seconds);
        tvProgressTime.setText(time);
    }

    // find closest PDR index
    private int findClosestPdrIndex(int timestamp, int pdrIndex) {
        // make sure index is within bounds
        int index = Math.min(Math.max(pdrIndex, 0), PdrNum - 1);

        while ((index < PdrNum - 1) &&
                (receTraj.getPdrData(index + 1).getRelativeTimestamp() <= timestamp)) {
            index++;
        }

        Log.d("ReplayFragment", "Closest PDR index: " + index);
        return index;
    }
}