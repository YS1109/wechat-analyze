package com.ysoztf.wechat.analyze.wxinfo.impl;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
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

    // 获取微信的进程Id
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

    // 获取注册表中微信的文件存储位置
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

    // 判断安装的微信是32位版本还是64位版本
    private boolean getIsWow64(int pid) {
        boolean isWow64 = false;
        // Windows 下的动态链接库，允许访问 Windows API
        Kernel32 kernel32 = Kernel32.INSTANCE;

        // 创建了一个进程快照，用于获取当前系统中运行的进程列表
        WinNT.HANDLE snapshot = kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        Tlhelp32.PROCESSENTRY32.ByReference processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();
        try {
            while (kernel32.Process32Next(snapshot, processEntry)) {
                // 通过遍历进程列表，找到特定 PID 对应的进程
                if (processEntry.th32ProcessID.intValue() == pid) {
                    // 使用 OpenProcess 打开该进程以获取它的处理权限
                    WinNT.HANDLE process = kernel32.OpenProcess(Kernel32.PROCESS_QUERY_INFORMATION, false, pid);
                    IntByReference wow64Process = new IntByReference(0);
                    // IsWow64Process 是用于确定指定进程是否为32位或64位的方法，它将返回一个标志，指示进程是32位还是64位
                    kernel32.IsWow64Process(process, wow64Process);
                    if (wow64Process.getValue() == 0) {
                        isWow64 = true;
                    }
                    break;
                }
            }
        } finally {
            kernel32.CloseHandle(snapshot);
        }
        return isWow64;
    }

    // 获取Wechat.exe的实际安装路径
    private String getWechatFileInstallPath(int pid) {
        String installPath = null;
        try {
            String command = "powershell.exe -Command \"Get-Process -Id " + pid + " | Select-Object Path\"";
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("WeChat.exe")) {
                    installPath = line;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return installPath;
    }

    // 获取安装微信的版本
    private String getWechatVersion(String weChatInstallPath) {
        String weChatVersion = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("powershell.exe", "-Command",
                    "& { Get-ItemProperty -Path \"" + weChatInstallPath + "\" | Format-List }");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FileVersion")) {
                    weChatVersion = line.split("\\s+")[2];
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return weChatVersion;
    }

    public static void main(String[] args) {
        WxInfoServiceImpl wxInfoService = new WxInfoServiceImpl();
        int pid = wxInfoService.getWechatPid();
        System.out.println(pid);
        String weChatFilePath = wxInfoService.getWechatFileBasePath();
        System.out.println(weChatFilePath);
        boolean isWow64 = wxInfoService.getIsWow64(wxInfoService.getWechatPid());
        System.out.println(isWow64);
        String weChatInstallPath = wxInfoService.getWechatFileInstallPath(pid);
        System.out.println(weChatInstallPath);
        String weChatVersion = wxInfoService.getWechatVersion(weChatInstallPath);
        System.out.println(weChatVersion);
    }
}
