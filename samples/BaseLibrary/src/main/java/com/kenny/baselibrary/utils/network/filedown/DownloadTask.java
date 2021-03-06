package com.kenny.baselibrary.utils.network.filedown;

import android.os.Handler;
import android.os.Looper;

import com.android.volley.VolleyError;
import com.kenny.baselibrary.utils.common.ZipUtils;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by kenny on 15/5/5.
 */
public class DownloadTask extends Thread {

    /**
     * 下载链接地址
     */
    private String downloadUrl;

    /**
     * 开启的线程数
     */
    private int threadNum;

    /**
     * 保存文件路径地址
     */
    private String filePath;

    /**
     * 每一个线程的下载量
     */
    private int blockSize;

    private final Listener listener;


    private int count = 0;

    private Handler mainHandler = new Handler(Looper.getMainLooper());


    private FileDownloadThread fileDownloadThread;


    private boolean isfinished = false;

    private DownFileInfo downFileInfo;


    private File file;

    private String realFileName;

    public DownloadTask(String downloadUrl, String fileptah, Listener listener) {
        this.downloadUrl = downloadUrl;
        this.realFileName = fileptah;
        this.filePath = fileptah + ".tmp";
        this.listener = listener;

    }


    @Override
    public void run() {

        downFileInfo = new DownFileInfo(DownFileInfo.DownState.progress, 0, 0);
        try {
            URL url = new URL(downloadUrl);
            URLConnection conn = url.openConnection();
            // 读取下载文件总大小
            int fileSize = conn.getContentLength();
            if (fileSize <= 0) {
                downFileInfo.setError(new VolleyError());
                mainHandler.post(new ResponseDeliveryRunnable(downFileInfo));
                return;
            }

            downFileInfo.setTotal(fileSize);

            file = new File(filePath);
            if (file.length() == fileSize) {
                downFileInfo.setCurrent(fileSize);
                downFileInfo.setState(DownFileInfo.DownState.success);
                mainHandler.post(new ResponseDeliveryRunnable(downFileInfo));
                return;
            } else if (file.length() != 0) {

                downFileInfo.setCurrent((int) file.length());
                downFileInfo.setState(DownFileInfo.DownState.progress);
                mainHandler.post(new ResponseDeliveryRunnable(downFileInfo));
            }
            // 启动线程，分别下载每个线程需要下载的部分
            fileDownloadThread = new FileDownloadThread(url, file, fileSize);
            fileDownloadThread.start();


            while (!isfinished) {
                if (fileDownloadThread.isError()) {
                    isfinished = true;
                    downFileInfo.setError(new VolleyError(fileDownloadThread.error));
                    downFileInfo.setState(DownFileInfo.DownState.error);
                    mainHandler.post(new ResponseDeliveryRunnable(downFileInfo));
                    return;
                }
                downFileInfo.setCurrent(fileDownloadThread.getDownloadLength());

                if (fileDownloadThread.isCompleted()) {
                    isfinished = true;
                    File realFile = new File(realFileName);
                    if (realFile.exists()) {
                        String nFileName = realFile.getName();
                        nFileName = nFileName.toLowerCase();
                        if (nFileName.endsWith(".zip")) {
                            ZipUtils.UnzipMoreFile(realFile, realFile.getParent());
                            realFile.delete();
                        }
                    }
                    downFileInfo.setState(DownFileInfo.DownState.success);
                } else {
                    downFileInfo.setState(DownFileInfo.DownState.progress);
                }
                mainHandler.post(new ResponseDeliveryRunnable(downFileInfo));
                Thread.sleep(100);

            }

        } catch (Exception e) {
            e.printStackTrace();
            deleteFile(e);
        }

    }

    public void deleteFile(Exception e) {
        downFileInfo.setError(new VolleyError(e));
        downFileInfo.setState(DownFileInfo.DownState.error);
        mainHandler.post(new ResponseDeliveryRunnable(downFileInfo));
        file.delete();
    }


    public void finish() {
        isfinished = true;
        if (fileDownloadThread != null)
            fileDownloadThread.finish();
    }


    public class ResponseDeliveryRunnable extends Thread {

        private DownFileInfo downFileInfo;


        public ResponseDeliveryRunnable(DownFileInfo downFileInfo) {
            this.downFileInfo = downFileInfo;
        }


        @Override
        public void run() {
            if (listener == null)
                return;
            ;

            switch (downFileInfo.getState()) {
                case success:
                    listener.success();
                    break;
                case error:
                    listener.error(downFileInfo.getError());
                    break;
                case progress:
                    listener.progress(downFileInfo.getTotal(), downFileInfo.getCurrent());
                    break;

            }
        }
    }


    public interface Listener {

        public void success();

        public void error(VolleyError error);

        public void progress(int total, int current);
    }


}
