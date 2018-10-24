package com.httpsgocentralph.mirroringwebrtc.Fragment;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.httpsgocentralph.mirroringwebrtc.MainActivity;
import com.httpsgocentralph.mirroringwebrtc.PeerListDialogFragment;
import com.httpsgocentralph.mirroringwebrtc.R;

import org.json.JSONArray;

import java.util.ArrayList;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

public class ShareFragment extends Fragment {
    private static final String TAG = ShareFragment.class.getSimpleName();
    //
    // Set your APIkey and Domain
    //
    private static final String API_KEY = "7b1b6dea-f688-48ed-b084-014dc7695c13";
    private static final String DOMAIN = "localhost";
    //
    // declaration
    //
    private Peer			_peer;
    private MediaStream		_localStream;
    private MediaStream		_remoteStream;
    private MediaConnection	_mediaConnection;

    private String _strOwnId;
    private boolean			_bConnected;

    private Handler _handler;
    View view;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_share, container, false);
        return view;
    }

    public void init(){
        //
        // Windows title hidden
        //
        Window wnd = getActivity().getWindow();
        wnd.addFlags(Window.FEATURE_NO_TITLE);
        getActivity().setContentView(R.layout.activity_main_videochat);
        //
        // Set UI handler
        //
        _handler = new Handler(Looper.getMainLooper());
        final Activity activity = getActivity();
        //
        // Initialize Peer
        //
        PeerOption option = new PeerOption();
        option.key = API_KEY;
        option.domain = DOMAIN;
        option.debug = Peer.DebugLevelEnum.ALL_LOGS;
        _peer = new Peer(view.getContext(), option);

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {

                // Show my ID
                _strOwnId = (String) object;
                // Request permissions
                if (ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},0);
                }
                else {

                    // Get a local MediaStream & show it
                    startLocalStream();

                }
            }
        });
        // ERROR
        _peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/Error]" + error);
            }
        });
        // CLOSE
        _peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback()	{
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Close]");
            }
        });
        // DISCONNECTED
        _peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Disconnected]");
            }
        });
        // CALL (Incoming call)
        _peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof MediaConnection)) {
                    return;
                }

                _mediaConnection = (MediaConnection) object;
                setMediaCallbacks();
                _mediaConnection.answer(_localStream);

                _bConnected = true;
                updateActionButtonTitle();
            }
        });
    }

    void startLocalStream() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.maxWidth = 960;
        constraints.maxHeight = 540;
        constraints.cameraPosition = MediaConstraints.CameraPositionEnum.FRONT;
        Navigator.initialize(_peer);
        _localStream = Navigator.getUserMedia(constraints);
        Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
        _localStream.addVideoRenderer(canvas,0);

    }

    //
    // Set callbacks for MediaConnection.MediaEvents
    //
    void setMediaCallbacks() {

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                _remoteStream = (MediaStream) object;
                Canvas canvas = (Canvas) findViewById(R.id.svRemoteView);
                _remoteStream.addVideoRenderer(canvas,0);
            }
        });

        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback()	{
            @Override
            public void onCallback(Object object) {
                closeRemoteStream();
                _bConnected = false;
                updateActionButtonTitle();
            }
        });
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, new OnCallback()	{
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;
                Log.d(TAG, "[On/MediaError]" + error);
            }
        });
    }
    //
    // Clean up objects
    //
    private void destroyPeer() {
        closeRemoteStream();

        if (null != _localStream) {
            Canvas canvas = (Canvas) findViewById(R.id.svLocalView);
            _localStream.removeVideoRenderer(canvas,0);
            _localStream.close();
        }

        if (null != _mediaConnection)	{
            if (_mediaConnection.isOpen()) {
                _mediaConnection.close();
            }
            unsetMediaCallbacks();
        }

        Navigator.terminate();

        if (null != _peer) {
            unsetPeerCallback(_peer);
            if (!_peer.isDisconnected()) {
                _peer.disconnect();
            }

            if (!_peer.isDestroyed()) {
                _peer.destroy();
            }

            _peer = null;
        }
    }
    //
    // Unset callbacks for PeerEvents
    //
    void unsetPeerCallback(Peer peer) {
        if(null == _peer){
            return;
        }

        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }
    //
    // Unset callbacks for MediaConnection.MediaEvents
    //
    void unsetMediaCallbacks() {
        if(null == _mediaConnection){
            return;
        }

        _mediaConnection.on(MediaConnection.MediaEventEnum.STREAM, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.CLOSE, null);
        _mediaConnection.on(MediaConnection.MediaEventEnum.ERROR, null);
    }
    //
    // Close a remote MediaStream
    //
    void closeRemoteStream(){
        if (null == _remoteStream) {
            return;
        }

        Canvas canvas = (Canvas) findViewById(R.id.svRemoteView);
        _remoteStream.removeVideoRenderer(canvas,0);
        _remoteStream.close();
    }

    //
    // Create a MediaConnection
    //
    void onPeerSelected(String strPeerId) {
        if (null == _peer) {
            return;
        }

        if (null != _mediaConnection) {
            _mediaConnection.close();
        }

        CallOption option = new CallOption();
        _mediaConnection = _peer.call(strPeerId, _localStream, option);

        if (null != _mediaConnection) {
            setMediaCallbacks();
            _bConnected = true;
        }

        updateActionButtonTitle();
    }
    //
    // Listing all peers
    //
    void showPeerIDs() {
        if ((null == _peer) || (null == _strOwnId) || (0 == _strOwnId.length())) {
            Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get all IDs connected to the server
        final Context fContext = this;
        _peer.listAllPeers(new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof JSONArray)) {
                    return;
                }

                JSONArray peers = (JSONArray) object;
                ArrayList<String> _listPeerIds = new ArrayList<>();
                String peerId;

                // Exclude my own ID
                for (int i = 0; peers.length() > i; i++) {
                    try {
                        peerId = peers.getString(i);
                        if (!_strOwnId.equals(peerId)) {
                            _listPeerIds.add(peerId);
                        }
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                }

                // Show IDs using DialogFragment
                if (0 < _listPeerIds.size()) {
                    FragmentManager mgr = getFragmentManager();
                    PeerListDialogFragment dialog = new PeerListDialogFragment();
                    dialog.setListener(
                            new PeerListDialogFragment.PeerListDialogFragmentListener() {
                                @Override
                                public void onItemClick(final String item) {
                                    _handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            onPeerSelected(item);
                                        }
                                    });
                                }
                            });
                    dialog.setItems(_listPeerIds);
                    dialog.show(mgr, "peerlist");
                }
                else{
                    Toast.makeText(fContext, "PeerID list (other than your ID) is empty.", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    //
    // Update actionButton title
    //
    void updateActionButtonTitle() {
        _handler.post(new Runnable() {
            @Override
            public void run() {
                Button btnAction = (Button) findViewById(R.id.btnAction);
                if (null != btnAction) {
                    if (false == _bConnected) {
                        btnAction.setText("Make Call");
                    } else {
                        btnAction.setText("Hang up");
                    }
                }
            }
        });
    }

}
