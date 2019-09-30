package com.izis.yzext.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;

import com.izis.yzext.net.NetWork;

import java.io.File;
import java.io.IOException;

import io.reactivex.Observer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * app更新管理类
 * Created by lxf on 2017/3/3.
 */
public class UpdateManager {

    /**
     * 是否需要更新,需要则下载
     *
     * @param context     上下文
     * @param url         新版本地址
     * @param apkPath     本地apk保存路径
     */
    public static void downloadApk(final Context context, final String url, final String apkPath, final CompositeDisposable cd) {
        NetWork.Companion.getInstance().down(url)
                .map(new Function<ResponseBody, BufferedSource>() {
                    @Override
                    public BufferedSource apply(ResponseBody responseBody) throws Exception {
                        return responseBody.source();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(new Observer<BufferedSource>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        System.out.println("UpdateManager.onSubscribe");
                        cd.add(d);
                    }

                    @Override
                    public void onNext(BufferedSource bufferedSource) {
                        System.out.println("UpdateManager.onNext");
                        try {
                            writeFile(bufferedSource, new File(apkPath));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("UpdateManager.onError");
                        unSubscribe(cd);
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("UpdateManager.onComplete");
                        //安装apk
                        installApk(apkPath,context);
//                        Intent intent = new Intent(Intent.ACTION_VIEW);
//                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        intent.setDataAndType(Uri.fromFile(new File(apkPath)), "application/vnd.android.package-archive");
//                        context.startActivity(intent);

                        unSubscribe(cd);
                    }
                });
    }


    /**
     * 写入文件
     */
    private static void writeFile(BufferedSource source, File file) throws IOException {
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        if (file.exists())
            file.delete();

        BufferedSink bufferedSink = Okio.buffer(Okio.sink(file));
        bufferedSink.writeAll(source);

        bufferedSink.close();
        source.close();
    }

    /**
     * 解除订阅
     *
     * @param cd 订阅关系集合
     */
    private static void unSubscribe(CompositeDisposable cd) {
        if (cd != null && !cd.isDisposed())
            cd.dispose();
    }

    /**
     * 安装APK文件
     */
    private static void installApk(String filePath,Context context) {
        File apkfile = new File(filePath);
        if (!apkfile.exists()) {
            return;
        }
        // 通过Intent安装APK文件
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(getUriForFile(context, apkfile),
                "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    private  static Uri getUriForFile(Context context, File file) {
        if (context == null || file == null) {
            throw new NullPointerException();
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(context.getApplicationContext(), "com.izis.yzext.fileprovider", file);
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }
}
