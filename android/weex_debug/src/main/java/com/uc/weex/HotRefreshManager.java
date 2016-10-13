/*
 * Copyright (C) 2004 - 2015 UCWeb Inc. All Rights Reserved.
 * Description :
 *
 * Creation    :  2016-09-07
 * Author      : huangq
 */

package com.uc.weex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ws.WebSocket;
import com.squareup.okhttp.ws.WebSocketCall;
import com.squareup.okhttp.ws.WebSocketListener;
import com.taobao.weex.WXEnvironment;
import com.taobao.weex.common.IWXDebugProxy;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import okio.Buffer;
import okio.BufferedSource;

/**
 */
public class HotRefreshManager implements android.os.Handler.Callback, HotRefreshManagerInterface {
    private static final String TAG = "HotRefreshManager";
    public static final int HOT_REFRESH_CONNECT = 1;
    public static final int HOT_REFRESH_DISCONNECT = 2;
    public static final int HOT_REFRESH_REFRESH = 3;
    public static final int HOT_REFRESH_CONNECT_ERROR = 4;

    private WebSocket mWebSocket = null;
    private Handler mHandler = null;
    private HotRefreshAdapter mRefreshAdapter;
    private RefreshBroadcastReceiver mReceiver = null;

    public HotRefreshManager(HotRefreshAdapter adapter) {
        mHandler = new Handler(this);
        mRefreshAdapter = adapter;
        registerBroadcastReceiver();
    }

    private void registerBroadcastReceiver() {
        mReceiver = new RefreshBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(IWXDebugProxy.ACTION_DEBUG_INSTANCE_REFRESH);
        WXEnvironment.getApplication().registerReceiver(mReceiver, filter);
    }

    private void unregisterBroadcastReceiver() {
        if (mReceiver != null) {
            WXEnvironment.getApplication().unregisterReceiver(mReceiver);
        }
    }

    @Override
    public void destroy() {
        mHandler.obtainMessage(HOT_REFRESH_DISCONNECT).sendToTarget();
        unregisterBroadcastReceiver();
    }

    private boolean disConnect() {
        if (mWebSocket != null) {
            try {
                mWebSocket.close(1000, "activity finish!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public void startHotRefresh(String url) {
        try {
            String host = new URL(url).getHost();
            String wsUrl = "ws://" + host + ":8082";
            mHandler.obtainMessage(HOT_REFRESH_CONNECT, 0, 0, wsUrl).sendToTarget();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private boolean connect(String url) {
        OkHttpClient httpClient = new OkHttpClient();
        Request request = new Request.Builder().url(url).addHeader("sec-websocket-protocol", "echo-protocol").build();
        WebSocketCall.create(httpClient, request).enqueue(new WXWebSocketListener());

        return true;
    }

    @Override
    public boolean handleMessage(Message msg) {

        switch (msg.what) {
            case HOT_REFRESH_CONNECT:
                connect(msg.obj.toString());
                break;
            case HOT_REFRESH_DISCONNECT:
                disConnect();
                break;
            case HOT_REFRESH_REFRESH:
                HotRefreshAdapter adapter = (HotRefreshAdapter) msg.obj;
                adapter.refreshIntance();
                break;
            case HOT_REFRESH_CONNECT_ERROR:
                //Toast.makeText(this, "hot refresh connect error!", Toast.LENGTH_SHORT).show();
                break;
        }

        return false;
    }

    private class WXWebSocketListener implements WebSocketListener {

        WXWebSocketListener() {
        }

        @Override
        public void onOpen(WebSocket webSocket, Request request, Response response) throws IOException {
            mWebSocket = webSocket;
        }

        @Override
        public void onMessage(BufferedSource payload, WebSocket.PayloadType type) throws IOException {
            if (type == WebSocket.PayloadType.TEXT) {
                String temp = payload.readUtf8();
                Log.e(TAG, "into--[onMessage] msg:" + temp);
                payload.close();

                if (TextUtils.equals("refresh", temp) && mHandler != null) {
                    mHandler.obtainMessage(HOT_REFRESH_REFRESH, mRefreshAdapter).sendToTarget();
                }
            }
        }

        @Override
        public void onPong(Buffer payload) {

        }

        @Override
        public void onClose(int code, String reason) {
            mWebSocket = null;
        }

        @Override
        public void onFailure(IOException e) {
            mWebSocket = null;
        }
    }

    private class RefreshBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (IWXDebugProxy.ACTION_DEBUG_INSTANCE_REFRESH.equals(intent.getAction())) {
                if (mRefreshAdapter.getBundleUrl() != null) {
                    if (mRefreshAdapter.getBundleUrl().startsWith("http://") || mRefreshAdapter.getBundleUrl().startsWith("https://")) {
                        mRefreshAdapter.refreshIntance();
                    }
                }
            }
        }
    }
}
