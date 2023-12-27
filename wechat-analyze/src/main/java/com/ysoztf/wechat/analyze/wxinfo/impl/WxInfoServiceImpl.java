package com.ysoztf.wechat.analyze.wxinfo.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.ysoztf.wechat.analyze.wxinfo.WxInfoService;
import com.ysoztf.wechat.analyze.wxinfo.beans.WxBiasInfo;
import com.ysoztf.wechat.analyze.wxinfo.beans.WxInfoBean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class WxInfoServiceImpl implements WxInfoService {
    private static Map<String, WxBiasInfo> WX_BIAS_OF_VERSION_MAP;

    public WxInfoServiceImpl() {
        if (WX_BIAS_OF_VERSION_MAP == null) {
            WX_BIAS_OF_VERSION_MAP = buildWxBiasOfVersionMap();
        }
    }

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

    // 构建地址偏移Map
    private Map<String, WxBiasInfo> buildWxBiasOfVersionMap() {
        Gson gson = new Gson();
        Map<String, WxBiasInfo> wxBiasOfVersionMap = new HashMap<>();
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            inputStream = this.getClass().getResourceAsStream("/versionList.json");
            reader = new BufferedReader(new InputStreamReader(inputStream));
            JsonObject versionJsonObject = gson.fromJson(reader, JsonObject.class);
            for (String version : versionJsonObject.keySet()) {
                WxBiasInfo wxBiasInfo = new WxBiasInfo();
                JsonArray wxBiasInfoJson = versionJsonObject.get(version).getAsJsonArray();
                wxBiasInfo.setNameBias(wxBiasInfoJson.get(0).getAsLong());
                wxBiasInfo.setAccountBias(wxBiasInfoJson.get(1).getAsLong());
                wxBiasInfo.setMobileBias(wxBiasInfoJson.get(2).getAsLong());
                wxBiasInfo.setMailBias(wxBiasInfoJson.get(3).getAsLong());
                wxBiasOfVersionMap.put(version, wxBiasInfo);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return wxBiasOfVersionMap;
    }

    // 获取微信内存基址
    private long getWeChatMemoryBaseAddr(int pid) {
        long baseAddr = 0L;
        Kernel32 kernel32 = Kernel32.INSTANCE;
        // TODO: 2023/12/27 32位和64位使用的DWORD不同，这里默认是64位的之后要兼容32位
        WinNT.HANDLE snapshot = kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPMODULE , new WinDef.DWORD(pid));
        Tlhelp32.MODULEENTRY32W.ByReference module = new Tlhelp32.MODULEENTRY32W.ByReference();

        try {
            boolean result = kernel32.Module32FirstW(snapshot, module);
            while (result) {
                if ("WeChatWin.dll".equals(Native.toString(module.szModule))) {
                    String modBaseAddr = module.modBaseAddr.toString();
                    String hexAddress = modBaseAddr.substring(modBaseAddr.indexOf("0x") + 2);
                    baseAddr = Long.parseLong(hexAddress, 16);
                    break;
                }
                result = kernel32.Module32NextW(snapshot, module);
            }
        } finally {
            kernel32.CloseHandle(snapshot);
        }
        return baseAddr;
    }

    // 获取或指定内存地址中的值
    private String getMemoryValue(int pid, long address, int bytesToRead) {
        Kernel32 kernel32 = Kernel32.INSTANCE;
        WinNT.HANDLE processHandle = kernel32.OpenProcess(Kernel32.PROCESS_VM_READ, false, pid);

        if (processHandle != null) {
            Pointer buffer = new Memory(bytesToRead); // 使用Memory初始化Pointer

            IntByReference bytesRead = new IntByReference(0);

            boolean success = kernel32.ReadProcessMemory(processHandle, new Pointer(address), buffer, bytesToRead, bytesRead);
            if (success) {
                byte[] result = new byte[bytesRead.getValue()];
                buffer.read(0, result, 0, bytesRead.getValue()); // 将数据从Pointer复制到byte[]
                return new String(result).trim();
            } else {
                System.err.println("Failed to read memory from address " + address);
            }
            kernel32.CloseHandle(processHandle);
        }
        return null;
    }

    private WxBiasInfo getCurrentVersionWxBiasInfo(String version) {
        return WX_BIAS_OF_VERSION_MAP.get(version);
    }

    public static void main(String[] args) {
        WxInfoServiceImpl wxInfoService = new WxInfoServiceImpl();
        int pid = wxInfoService.getWechatPid();
        String weChatFilePath = wxInfoService.getWechatFileBasePath();
        boolean isWow64 = wxInfoService.getIsWow64(wxInfoService.getWechatPid());
        String weChatInstallPath = wxInfoService.getWechatFileInstallPath(pid);
        String weChatVersion = wxInfoService.getWechatVersion(weChatInstallPath);
        long baseAddr = wxInfoService.getWeChatMemoryBaseAddr(pid);

        WxBiasInfo wxBiasInfo = wxInfoService.getCurrentVersionWxBiasInfo(weChatVersion);
        String account = wxInfoService.getMemoryValue(pid, baseAddr + wxBiasInfo.getAccountBias(), 32);
        String mobile = wxInfoService.getMemoryValue(pid, baseAddr + wxBiasInfo.getMobileBias(), 32);
        String name = wxInfoService.getMemoryValue(pid, baseAddr + wxBiasInfo.getNameBias(), 32);

        System.out.println("pid: " + pid);
        System.out.println("weChatFilePath: " +weChatFilePath);
        System.out.println("isWow64: " + isWow64);
        System.out.println("weChatInstallPath: " + weChatInstallPath);
        System.out.println("weChatVersion:" + weChatVersion);
        System.out.println("baseAddr:" + baseAddr);
        System.out.println("account:" + account);
        System.out.println("mobile:" + mobile);
        System.out.println("name:" + name);
    }
}
