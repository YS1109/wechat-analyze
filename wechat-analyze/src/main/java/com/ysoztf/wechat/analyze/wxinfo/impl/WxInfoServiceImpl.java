package com.ysoztf.wechat.analyze.wxinfo.impl;

import com.ysoztf.wechat.analyze.wxinfo.WxInfoService;
import com.ysoztf.wechat.analyze.wxinfo.beans.WxInfoBean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class WxInfoServiceImpl implements WxInfoService {
    @Override
    public WxInfoBean loadWxInfo() {
        return null;
    }

    private int getWechatPid() {
        int pid = 0;
        try {
            // Windows 系统命令
            String[] command = {"cmd.exe", "/c", "tasklist /FI \"IMAGENAME eq WeChat.exe\" /FO TABLE"};
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            // 读取进程列表
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("WeChat.exe")) {
                    String[] params = line.split("\\s+");
                    if (params.length >= 4) {
                        pid = Integer.parseInt(params[1]);
                        break;
                    }
                    System.out.println(line);
                }
            }
            // 关闭流
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pid;
    }

    private String getWechatFileBasePath() {
        String fileBasePath = null;
        try {
            // Windows 系统命令
            String[] command = {"cmd.exe", "/c",
                    "reg query HKEY_CURRENT_USER\\SOFTWARE\\Tencent\\WeChat /v FileSavePath"};
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FileSavePath")) {
                    String[] params = line.split("\\s+");
                    if (params.length >= 4) {
                        fileBasePath = params[3];
                    }
                }
            }
            // 关闭流
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileBasePath;
    }

    public static void main(String[] args) {
        WxInfoServiceImpl wxInfoService = new WxInfoServiceImpl();
        System.out.println(wxInfoService.getWechatPid());
        System.out.println(wxInfoService.getWechatFileBasePath());
    }
}
