package com.openpositioning.PositionMe;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ReplayFragment extends Fragment {

    private boolean isPlaying = false; // 初始状态为未播放
    private int progress = 0; // 记录进度条进度
    // Responsible for updating UI in Loop
    private Handler refreshDataHandler;
    private Traj.Trajectory receTraj;
    private int PdrNum;
    private int GnssNum;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // read the trajectory data
        readTrajectoryData();

        // initialize refreshDataHandler
        this.refreshDataHandler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载布局文件
        View view = inflater.inflate(R.layout.fragment_replay, container, false);

        // 初始化控件
        ImageButton playPauseButton = view.findViewById(R.id.btn_play_pause);
        Button restartButton = view.findViewById(R.id.btn_restart);
        Button goToEndButton = view.findViewById(R.id.btn_go_to_end);
        Button exitButton = view.findViewById(R.id.btn_exit);
        SeekBar seekBar = view.findViewById(R.id.seek_bar);

        // 设置播放/暂停按钮点击事件
        playPauseButton.setOnClickListener(v -> {
            if (isPlaying) {
                pausePlayback();
                playPauseButton.setImageResource(R.drawable.ic_baseline_play_circle_filled_24_b); // 切换为播放图标
            } else {
                startPlayback();
                playPauseButton.setImageResource(R.drawable.ic_baseline_pause_circle_filled_24); // 切换为暂停图标
            }
            isPlaying = !isPlaying; // 切换状态
        });

        // 设置 Restart 按钮点击事件
        restartButton.setOnClickListener(v -> {
            progress = 0; // 重置进度
            seekBar.setProgress(progress);
            Toast.makeText(getContext(), "Restart button clicked. Progress reset.", Toast.LENGTH_SHORT).show();
        });

        // 设置 Go to End 按钮点击事件
        goToEndButton.setOnClickListener(v -> {
            progress = seekBar.getMax(); // 将进度条设置为最大值
            seekBar.setProgress(progress);
            Toast.makeText(getContext(), "Go to End button clicked. Progress set to max.", Toast.LENGTH_SHORT).show();
        });

        // 设置 Exit 按钮点击事件
        exitButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Exit button clicked. Exiting...", Toast.LENGTH_SHORT).show();
            requireActivity().onBackPressed(); // 返回上一个页面
        });


        Switch gnssSwitch = view.findViewById(R.id.gnssSwitch);

        gnssSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Toast.makeText(getContext(), "GNSS Enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "GNSS Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置进度条监听器
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updatePlaybackPosition(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getContext(), "Started dragging SeekBar", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getContext(), "Stopped dragging SeekBar", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void startPlayback() {
        Toast.makeText(getContext(), "Playback started", Toast.LENGTH_SHORT).show();

        updateUIandPosition();
        refreshDataHandler.postDelayed(refreshDataTask, 200);
        
    }

    private void pausePlayback() {
        Toast.makeText(getContext(), "Playback paused", Toast.LENGTH_SHORT).show();
        // stop updateUIandPosition
    }

    private void updatePlaybackPosition(int progress) {
        Toast.makeText(getContext(), "Playback position updated: " + progress, Toast.LENGTH_SHORT).show();
    }

    private void readTrajectoryData() {
        String storagePath = context.getFilesDir().toString();

        // read file "received_trajectory.txt" from storagePath
        File file = new File(storagePath, "received_trajectory.txt");
        try {
            // check if file exists
            if (file.exists()) {
                // read file using BufferedReader
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                StringBuilder content = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                // parse file content
                receTraj = Traj.Trajectory.parseFrom(content.toString().getBytes());
                PdrNum = receTraj.getPdrCount();
                GnssNum = receTraj.getGnssCount();
            } else {
                Log.e("FileError", "文件不存在");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateUIandPosition() {
        // 根据 progress 计算 relatedTimeStamp，relatedTimeStamp 单位是 ms

        // 从 receTraj 查找距离 relatedTimeStamp 最近的一个 PDR 点
        // 可用的函数如下，其中 index 是一个 从 0 - （PdrNum-1） 的 int
        // receTraj.getPdrData(index).getRelativeTimestamp();
        // receTraj.getPdrData(index).getX();
        // receTraj.getPdrData(index).getY();

        // 画图
        // using plotLines

        // 从 receTraj 查找距离 relatedTimeStamp 最近的一个 GNSS 点
        // 标注一个 marker 在地图上
        // 可用的函数如下，其中 index 是一个 从 0 - （GnssNum-1） 的 int
        // receTraj.getGnssData(index).getLatitude();
        // receTraj.getGnssData(index).getLongitude();
        // receTraj.getGnssData(index).getAltitude();
        // receTraj.getGnssData(index).getRelativeTimestamp();

        // 更新 progress
    }

    private void plotLines(float[] pdrMoved){
        // draw a line in the maps
    }
}
