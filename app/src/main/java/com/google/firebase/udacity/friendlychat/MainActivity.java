/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int SIGN_IN_CODE = 128;
    private static final int PHOTO_PICKER_CODE = 129;
    private static final String MESSAGE_LENGTH_CONFIG = "message_length";

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    // Firebase DB related
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessagesDatabaseReference;
    private ChildEventListener mMessagesListener;

    // Firebase Auth related
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    // Firebase Storage related
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;

    // FIrebase Remote Config related
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        // Initialize references to views
        mProgressBar =  findViewById(R.id.progressBar);
        mMessageListView = findViewById(R.id.messageListView);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String promptText = getString(R.string.complete_using);
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, promptText), PHOTO_PICKER_CODE);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mSendButton.setEnabled(charSequence.toString().trim().length() > 0);
            }
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void afterTextChanged(Editable editable) { }
        });
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {
               FriendlyMessage friendlyMessage = new FriendlyMessage(
                       mMessageEditText.getText().toString(), mUsername, null);
               mMessagesDatabaseReference.push().setValue(friendlyMessage);
               mMessageEditText.setText("");
           }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null){   // User signed
                    OnSignInInit(user.getDisplayName());
                } else {    // User not signed
                    OnSignOutClear();
                    Intent signInIntent = AuthUI.getInstance().createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false)
                            .setAvailableProviders(Arrays.asList(
                                    new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                            .build();
                    startActivityForResult(signInIntent, SIGN_IN_CODE);
                }
            }
        };

        FirebaseRemoteConfigSettings remoteConfig = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG) // true developing, false at release
                .build();
        mFirebaseRemoteConfig.setConfigSettings(remoteConfig);

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(MESSAGE_LENGTH_CONFIG, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        fetchConfig();
    }

    private void fetchConfig() {
        // Cache expiration, 1 hour to production, 0 for development
        long cache = mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled() ?
                0 : 3600;
        mFirebaseRemoteConfig.fetch(cache).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFirebaseRemoteConfig.activateFetched();
                applyFetchedConfig();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "Error fetching RemoteConfig");
                applyFetchedConfig();   // Makes sense because the cache MIGHT be different
            }
        });
    }

    private void applyFetchedConfig() {
        Long message_length = mFirebaseRemoteConfig.getLong(MESSAGE_LENGTH_CONFIG);
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(message_length.intValue())});
        Log.d(TAG, MESSAGE_LENGTH_CONFIG + " set to " + message_length + " by RemoteConfig");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case SIGN_IN_CODE:
                if(resultCode == RESULT_CANCELED)
                    finish();
                break;
            case PHOTO_PICKER_CODE:
                if(resultCode != RESULT_OK)
                    return;

                Uri selectedImageUri = data.getData();
                if(selectedImageUri == null) {
                    Toast.makeText(this, R.string.problems_photo_picker, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                String photoFolder = selectedImageUri.getLastPathSegment();
                StorageReference photoRef = mChatPhotosStorageReference.child(photoFolder);
                photoRef.putFile(selectedImageUri).addOnSuccessListener(
                        new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                Uri photoFirebaseUrl = taskSnapshot.getDownloadUrl();
                                if(photoFirebaseUrl == null) {
                                    Toast.makeText(MainActivity.this,
                                            R.string.problems_photo_picker, Toast.LENGTH_SHORT)
                                            .show();
                                    return;
                                }
                                FriendlyMessage message = new FriendlyMessage(null, mUsername,
                                        photoFirebaseUrl.toString());
                                mMessagesDatabaseReference.push().setValue(message);
                            }
                        });
                break;
        }
    }

    private void OnSignInInit(String username) {
        mUsername = username;
        attachEventListeners();
    }

    private void OnSignOutClear() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachEventListeners();
    }

    private void attachEventListeners(){
        if(mMessagesListener != null)
            return;

        // Listen for DB changes
        mMessagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                FriendlyMessage message = dataSnapshot.getValue(FriendlyMessage.class);
                mMessageAdapter.add(message);
            }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) { }
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) { }
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) { }
            @Override
            public void onCancelled(DatabaseError databaseError) { }
        };
        mMessagesDatabaseReference.addChildEventListener(mMessagesListener);
    }

    private void detachEventListeners(){
        if(mMessagesListener == null)
            return;

        mMessagesDatabaseReference.removeEventListener(mMessagesListener);
        mMessagesListener = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if( mAuthStateListener != null)
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);

        // As onResume will call OnSignInInit, clear the adapter and detach the listener
        mMessageAdapter.clear();
        detachEventListeners();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case R.id.sign_out_menu:    // Sign out from Firebase
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
