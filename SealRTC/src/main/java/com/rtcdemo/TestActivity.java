package com.rtcdemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcel;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import cn.rongcloud.rtc.RTCErrorCode;
import cn.rongcloud.rtc.RongRTCConfig;
import cn.rongcloud.rtc.RongRTCConfig.RongRTCVideoProfile;
import cn.rongcloud.rtc.RongRTCEngine;
import cn.rongcloud.rtc.callback.JoinRoomUICallBack;
import cn.rongcloud.rtc.callback.RongRTCResultCallBack;
import cn.rongcloud.rtc.callback.RongRTCResultUICallBack;
import cn.rongcloud.rtc.core.RendererCommon;
import cn.rongcloud.rtc.engine.report.StatusReport;
import cn.rongcloud.rtc.engine.view.RongRTCVideoView;
import cn.rongcloud.rtc.events.ILocalAudioPCMBufferListener;
import cn.rongcloud.rtc.events.ILocalVideoFrameListener;
import cn.rongcloud.rtc.events.IRemoteAudioPCMBufferListener;
import cn.rongcloud.rtc.events.RTCAudioFrame;
import cn.rongcloud.rtc.events.RTCVideoFrame;
import cn.rongcloud.rtc.events.RongRTCEventsListener;
import cn.rongcloud.rtc.events.RongRTCStatusReportListener;
import cn.rongcloud.rtc.room.RongRTCRoom;
import cn.rongcloud.rtc.stream.MediaType;
import cn.rongcloud.rtc.stream.local.RongRTCAVOutputStream;
import cn.rongcloud.rtc.stream.local.RongRTCCapture;
import cn.rongcloud.rtc.stream.remote.RongRTCAVInputStream;
import cn.rongcloud.rtc.user.RongRTCRemoteUser;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.RongIMClient.ConnectCallback;
import io.rong.imlib.RongIMClient.ErrorCode;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import java.util.HashMap;
import java.util.List;


public class TestActivity extends Activity {


  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.login_activity_layout);
    test();
  }

  private void test() {
    RongIMClient.init(getApplication());
    RongIMClient.connect("token", new ConnectCallback() {
      @Override
      public void onTokenIncorrect() {

      }

      @Override
      public void onSuccess(String userId) {
        joinRoom();
      }

      @Override
      public void onError(ErrorCode errorCode) {

      }
    });
  }

  private RongRTCRoom room;

  private void joinRoom() {
    RongRTCConfig.Builder configBuilder = new RongRTCConfig.Builder();
    configBuilder.setVideoProfile(RongRTCVideoProfile.RONGRTC_VIDEO_PROFILE_480P_15f_1);
    configBuilder.enableTinyStream(false);
    //默认true,如果设置为false，网络异常rtcPing 连续4次失败时会主动退出房间，callLib 会设置false
    configBuilder.enableAutoReconnect(true);
    //默认为false，私有部署开发者运维配置的签名证书为自签证书时需要设置为true
    //true时会绕过https证书校验过程
    configBuilder.enableHttpsSelfCertificate(true);
    RongRTCEngine.getInstance().joinRoom("roomId", new JoinRoomUICallBack() {
      @Override
      protected void onUiSuccess(RongRTCRoom rongRTCRoom) {
        room = rongRTCRoom;
        RongRTCVideoView localVideoView = RongRTCEngine.getInstance()
            .createVideoView(TestActivity.this);
        RongRTCCapture.getInstance().setRongRTCVideoView(localVideoView);
        RongRTCCapture.getInstance().startCameraCapture();
        //发布资源
        publishResource();
        //设置加入房间后的事件监听
        addRoomEventListener();
        //加入房间时，房间内已经有用户发布过资源
        subscribeResource();
        //发布自定义音频流
        publishCustomStream();
        //发布音视频流或者订阅别人的音视频流后，音视频流的网络传输状态统计
        registerStatusReportListener();
        //本地和远端音频原始pcm数据流回调给app层，app 层可以做一些处理后返回给RTCLib
        //可以分别录制本地和远端的音频，RTCLib的录音功能目前在4.0.0+开发中，还未上线
        registerAudioBufferListener();
        //本地摄像头采集的视频流回调,美颜处理
        registerLocalVideoBufferListener();
        //设置房间属性，做一些特殊化的定制需求
        setRoomAttribute();
        //设置我自己的属性
        setLocalUserAttribute();
      }

      @Override
      protected void onUiFailed(RTCErrorCode rtcErrorCode) {

      }
    });
  }


  private void publishResource() {
    //发布SDK默认支持的音视频流，默认采集麦克风声音，前置摄像头视频
    room.getLocalUser().publishDefaultAVStream(new RongRTCResultUICallBack() {
      @Override
      public void onUiSuccess() {

      }

      @Override
      public void onUiFailed(RTCErrorCode rtcErrorCode) {

      }
    });
    //如果想只发布SDK支持的默认音频资源，不发布视频资源
    room.getLocalUser().publishAVStream(room.getLocalUser().getDefaultAudioStream(),
        new RongRTCResultUICallBack() {
          @Override
          public void onUiSuccess() {

          }

          @Override
          public void onUiFailed(RTCErrorCode rtcErrorCode) {

          }
        });
  }

  private void addRoomEventListener() {
    room.registerEventsListener(new RongRTCEventsListener() {
      //远端用户发布资源,streams 是新发布的资源
      @Override
      public void onRemoteUserPublishResource(RongRTCRemoteUser rongRTCRemoteUser,
          List<RongRTCAVInputStream> streams) {
        subscribeResource(rongRTCRemoteUser, streams);
      }

      //远端用户静音或者开启麦克风
      @Override
      public void onRemoteUserAudioStreamMute(RongRTCRemoteUser rongRTCRemoteUser,
          RongRTCAVInputStream rongRTCAVInputStream, boolean mute) {

      }

      //远端用户打开或者关闭摄像头
      @Override
      public void onRemoteUserVideoStreamEnabled(RongRTCRemoteUser rongRTCRemoteUser,
          RongRTCAVInputStream rongRTCAVInputStream, boolean mute) {

      }
      //远端用户取消发布资源,streams 是取消发布的资源
      @Override
      public void onRemoteUserUnpublishResource(RongRTCRemoteUser rongRTCRemoteUser,
          List<RongRTCAVInputStream> streams) {
        //远端用户取消发布资源，如果是视频资源，app 层 移除 RongRTCVideoView 即可
        // RTCLib SDK 内部已经做了取消订阅资源的操作，不需要开发者主动在这个通知里取消订阅资源
      }

      //远端用户加入房间，指加入IM server 信令房间，此时通常情况下还没有发布资源
      @Override
      public void onUserJoined(RongRTCRemoteUser rongRTCRemoteUser) {

      }

      //远端用户主动离开入房间，SDK内部会主动取消订阅这个用户发布的资源，app层做UI更新即可
      @Override
      public void onUserLeft(RongRTCRemoteUser rongRTCRemoteUser) {

      }

      //远端用户被 IM server踢下房间，SDK内部会主动取消订阅这个用户发布的资源，app层做UI更新即可
      //用户加入房间后，默认每10s发一次 rtcPing 到 IM  server,如果因为本地断网等原因长时间没有rtcPing
      //IM server 在大概 1分钟会把这个用户踢除房间
      @Override
      public void onUserOffline(RongRTCRemoteUser rongRTCRemoteUser) {

      }

      //底层媒体引擎层 webRTC 上报的通知事件，订阅远端视频流成功后，用于传输远端视频流的 videoTrack 在本地创建成功
      //此时通过webRTC udp 通道传输的远端视频流还没有到达本地
      @Override
      public void onVideoTrackAdd(String userId, String tag) {

      }

      //渲染 RongRTCVideoView的第一帧回调通知
      //如果是远端用户的RongRTCVideoView，onFirstFrameDraw的调用肯定是发生在onVideoTrackAdd之后
      @Override
      public void onFirstFrameDraw(String userId, String tag) {

      }

      //因一些异常原因，RTCLib SDK内部主动退出房间
      //对应config配置中的enableAutoReconnect
      @Override
      public void onLeaveRoom() {

      }

      //接收到房间内 开发者自定义IM消息
      //注意，这个回调只能接收到当前加入房间内的消息，不会收到融云IM的私聊，群组，聊天室等的消息
      @Override
      public void onReceiveMessage(Message message) {

      }
    });
  }

  private void subscribeResource() {
    for (RongRTCRemoteUser remoteUser : room.getRemoteUsers().values()) {
      subscribeResource(remoteUser, remoteUser.getRemoteAVStreams());
    }
  }

  private void subscribeResource(RongRTCRemoteUser remoteUser, List<RongRTCAVInputStream> streams) {
    for (RongRTCAVInputStream inputStream : remoteUser.getRemoteAVStreams()) {
      if (inputStream.getMediaType() == MediaType.VIDEO) {
        RongRTCVideoView videoView = RongRTCEngine.getInstance().createVideoView(this);
        inputStream.setRongRTCVideoView(videoView);
      }
    }
    remoteUser.subscribeAVStream(remoteUser.getRemoteAVStreams(), new RongRTCResultUICallBack() {
      @Override
      public void onUiSuccess() {

      }

      @Override
      public void onUiFailed(RTCErrorCode rtcErrorCode) {
      }
    });
  }


  private void publishCustomStream() {
    RongRTCAVOutputStream outputStream = new RongRTCAVOutputStream(
        cn.rongcloud.rtc.stream.MediaType.VIDEO, "Custom");
    RongRTCVideoView videoView = RongRTCEngine.getInstance().createVideoView(this);
    outputStream.setRongRTCVideoView(videoView);
    room.getLocalUser().publishAVStream(outputStream, new RongRTCResultUICallBack() {
      @Override
      public void onUiSuccess() {
      }

      @Override
      public void onUiFailed(RTCErrorCode errorCode) {
      }
    });
    byte[] bytes = null;
    int width = 0;
    int height = 0;
    int oesTextureId = 0;
    float[] transformMatrix = null;
    int rotation = 0;
    long timestamp = 0;
    //byteBuffer
    outputStream.writeByteBuffer(bytes, width, height, rotation);
    //或者 texture
    outputStream
        .writeTextureFrame(width, height, oesTextureId, transformMatrix, rotation, timestamp);
  }

  private void registerStatusReportListener() {
    room.registerStatusReportListener(new RongRTCStatusReportListener() {
      @Override
      public void onAudioReceivedLevel(HashMap<String, String> hashMap) {

      }

      @Override
      public void onAudioInputLevel(String s) {

      }

      @Override
      public void onConnectionStats(StatusReport statusReport) {

      }
    });
  }

  private void registerAudioBufferListener() {
    RongRTCCapture.getInstance().setLocalAudioPCMBufferListener(new ILocalAudioPCMBufferListener() {
      @Override
      public byte[] onLocalBuffer(RTCAudioFrame rtcAudioFrame) {
        return new byte[0];
      }
    });
    RongRTCCapture.getInstance().setRemoteAudioPCMBufferListener(
        new IRemoteAudioPCMBufferListener() {
          @Override
          public byte[] onRemoteBuffer(RTCAudioFrame rtcAudioFrame) {
            return new byte[0];
          }
        });
  }

  private void registerLocalVideoBufferListener() {
    RongRTCCapture.getInstance().setLocalVideoFrameListener(true, new ILocalVideoFrameListener() {
      @Override
      public RTCVideoFrame processVideoFrame(RTCVideoFrame rtcVideoFrame) {
        return null;
      }
    });
  }

  private void setRoomAttribute() {
    //IM自定义消息
    MessageContent messageContent;
    room.setRoomAttributeValue("key", "value", null, new RongRTCResultCallBack() {
      @Override
      public void onSuccess() {

      }

      @Override
      public void onFailed(RTCErrorCode rtcErrorCode) {

      }
    });
  }

  private void setLocalUserAttribute() {
    //IM自定义消息
    MessageContent messageContent = null;
    room.getLocalUser()
        .setAttributeValue("key", "value", messageContent, new RongRTCResultCallBack() {
          @Override
          public void onSuccess() {

          }

          @Override
          public void onFailed(RTCErrorCode rtcErrorCode) {

          }
        });
  }

}
