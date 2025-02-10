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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private List<LatLng> pdrCoordinates = new ArrayList<>();
    private List<LatLng> gnssCoordinates = new ArrayList<>();
    private long totalDuration = 0;
    private TextView tvProgressTime; // 新增时间显示控件

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readTrajectoryData(); // 加载轨迹数据
        refreshDataHandler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_replay, container, false);
        requireActivity().setTitle("Replay"); // 设置标题

        // 初始化地图
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_container);
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

    // 地图初始化
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        gMap.getUiSettings().setZoomControlsEnabled(true);

        if (!pdrCoordinates.isEmpty()) {
            LatLng initialPos = pdrCoordinates.get(0);
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 19f));
            positionMarker = gMap.addMarker(new MarkerOptions().position(initialPos).title("Position"));
            PolylineOptions options = new PolylineOptions().color(Color.BLUE).width(8f);
            polyline = gMap.addPolyline(options);
        }
    }

    // 读取轨迹数据（保持原有逻辑）
    private void readTrajectoryData() {
        File file = new File(requireContext().getFilesDir(), "received_trajectory.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            if (file.exists()) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) content.append(line);
                receTraj = Traj.Trajectory.parseFrom(content.toString().getBytes());
                PdrNum = receTraj.getPdrDataCount();
                GnssNum = receTraj.getGnssDataCount();


                for (int i = 0; i < PdrNum; i++) {
                    Traj.Pdr_Sample pdr = receTraj.getPdrData(i);
                    pdrCoordinates.add(new LatLng(pdr.getX(), pdr.getY()));
                }

                if (PdrNum > 0) {
                    totalDuration = receTraj.getPdrData(PdrNum - 1).getRelativeTimestamp();
                    seekBar.setMax((int) totalDuration);
                }
            }
        } catch (IOException e) {
            Log.e("ReplayFragment", "Error reading trajectory", e);
        }
    }

    // 开始播放
    private void startPlayback() {
        refreshDataHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isPlaying && progress < totalDuration) {
                    progress += 200;
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

    // 更新地图和UI
    private void updateUIandPosition(int progress) {
        if (pdrCoordinates.isEmpty() || polyline == null) return;
        int index = findClosestPdrIndex(progress);
        LatLng currentPos = pdrCoordinates.get(index);

        List<LatLng> points = new ArrayList<>(pdrCoordinates.subList(0, index + 1));
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

    // 二分查找最近PDR索引（保持原有逻辑）
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