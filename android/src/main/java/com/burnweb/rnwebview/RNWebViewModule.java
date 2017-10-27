package com.burnweb.rnwebview;

import android.annotation.SuppressLint;
import com.facebook.react.common.annotations.VisibleForTesting;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

import android.widget.Toast;
import android.provider.MediaStore;
import android.os.Parcelable;
import android.os.Environment;
import java.io.File;
import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

public class RNWebViewModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    @VisibleForTesting
    public static final String REACT_CLASS = "RNWebViewAndroidModule";

    private RNWebViewPackage aPackage;
    private Uri mCapturedImageURI = null;
    private String mCameraPhotoPath;
    private ValueCallback<Uri[]> mFilePathCallback;

    /* FOR UPLOAD DIALOG */
    private final static int REQUEST_SELECT_FILE = 1001;
    private final static int REQUEST_SELECT_FILE_LEGACY = 1002;

    private ValueCallback<Uri> mUploadMessage = null;
    private ValueCallback<Uri[]> mUploadMessageArr = null;

    public RNWebViewModule(ReactApplicationContext reactContext) {
        super(reactContext);

        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    public void setPackage(RNWebViewPackage aPackage) {
        this.aPackage = aPackage;
    }

    public RNWebViewPackage getPackage() {
        return this.aPackage;
    }

    @SuppressWarnings("unused")
    public Activity getActivity() {
        return getCurrentActivity();
    }

    public void showAlert(String url, String message, final JsResult result) {
        AlertDialog ad = new AlertDialog.Builder(getCurrentActivity())
                                .setMessage(message)
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        result.confirm();
                                    }
                                })
                                .create();

        ad.show();
    }

    // For Android 4.1+
    @SuppressWarnings("unused")
    public boolean startFileChooserIntent(ValueCallback<Uri> uploadMsg, String acceptType) {
        Log.d(REACT_CLASS, "Open old file dialog");

        if (mUploadMessage != null) {
            mUploadMessage.onReceiveValue(null);
            mUploadMessage = null;
        }

        mUploadMessage = uploadMsg;

        if (acceptType == null || acceptType.isEmpty()) {
            acceptType = "*/*";
        }


        // NEW PART
        // Create AndroidExampleFolder at sdcard
        File imageStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "html-files");

        if (!imageStorageDir.exists()) {
          // Create AndroidExampleFolder at sdcard
          imageStorageDir.mkdirs();
        }

        // Create camera captured image file path and name
        File file = new File(imageStorageDir + File.separator + "IMG_" + String.valueOf(System.currentTimeMillis()) + ".jpg");

        mCapturedImageURI = Uri.fromFile(file);

        // Camera capture image intent
        final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);
        // NEW PART END


        Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
        filePicker.addCategory(Intent.CATEGORY_OPENABLE);
        filePicker.setType(acceptType);


        // NEW PART
        // Create file chooser intent
        Intent chooserIntent = Intent.createChooser(filePicker, "Image Chooser");

        // Set camera intent to file chooser
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[] { captureIntent });
        // NEW PART


        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            Log.w(REACT_CLASS, "No context available");
            return false;
        }

        try {
            currentActivity.startActivityForResult(chooserIntent, REQUEST_SELECT_FILE_LEGACY, new Bundle());
        } catch (ActivityNotFoundException e) {
            Log.e(REACT_CLASS, "No context available");
            e.printStackTrace();
            Toast.makeText(currentActivity.getBaseContext(), "Exception:" + e, Toast.LENGTH_LONG).show();

            if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(null);
                mUploadMessage = null;
            }
            return false;
        }

        return true;
    }

    // For Android 5.0+
    @SuppressLint("NewApi")
    public boolean startFileChooserIntent(ValueCallback<Uri[]> filePathCallback, Intent intentChoose) {
        Log.d(REACT_CLASS, "Open new file dialog");

        // if (mUploadMessageArr != null) {
        //     mUploadMessageArr.onReceiveValue(null);
        //     mUploadMessageArr = null;
        // }
        //
        // mUploadMessageArr = filePathCallback;

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            Log.w(REACT_CLASS, "No context available");
            return false;
        }


        // NEW CODE
        // Double check that we don't have any existing callbacks
        if (mFilePathCallback != null) {
          mFilePathCallback.onReceiveValue(null);
        }

        mFilePathCallback = filePathCallback;

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(currentActivity.getPackageManager()) != null) {
          // Create the File where the photo should go
          File photoFile = null;

          try {
            photoFile = createImageFile();
            takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
          } catch (IOException ex) {
            // Error occurred while creating the File
            Log.e(REACT_CLASS, "Unable to create Image File", ex);
            Toast.makeText(currentActivity.getBaseContext(), "Unable to create Image File:" + ex, Toast.LENGTH_LONG).show();
          }

          // Continue only if the File was successfully created
          if (photoFile != null) {
            mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
          } else {
            takePictureIntent = null;
          }
        }

        // Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        // contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        // contentSelectionIntent.setType("image/*");

        Intent[] intentArray;

        if (takePictureIntent != null) {
          intentArray = new Intent[]{takePictureIntent};
        } else {
          intentArray = new Intent[0];
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intentChoose);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
        // NEW CODE END

        try {
            currentActivity.startActivityForResult(chooserIntent, REQUEST_SELECT_FILE, new Bundle());
        } catch (ActivityNotFoundException e) {
            Log.e(REACT_CLASS, "No context available");
            e.printStackTrace();
            Toast.makeText(currentActivity.getBaseContext(), "No context available:" + e, Toast.LENGTH_LONG).show();

            if (mUploadMessageArr != null) {
                mUploadMessageArr.onReceiveValue(null);
                mUploadMessageArr = null;
            }
            return false;
        }

        return true;
    }

    @SuppressLint({"NewApi", "Deprecated"})
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SELECT_FILE_LEGACY) {
            if (mUploadMessage == null) return;
            if (mCapturedImageURI == null) return;

            if (resultCode == Activity.RESULT_OK) {
              Uri result = (data == null ? mCapturedImageURI : data.getData());

              mUploadMessage.onReceiveValue(result);
            } else {
              Uri result = null;

              mUploadMessage.onReceiveValue(result);
            }

            mUploadMessage = null;
            mCapturedImageURI = null;
        } else if (requestCode == REQUEST_SELECT_FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // if (mUploadMessageArr == null) return;
            //
            // mUploadMessageArr.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            // mUploadMessageArr = null;

            if (requestCode != REQUEST_SELECT_FILE || mFilePathCallback == null) {
                return;
            }

            Uri[] results = null;

            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                Log.e(REACT_CLASS, "WEBVIEW DATA: " + data);
                if (data == null) {
                    // If there is no data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                      results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                } else {
                    String dataString = data.getDataString();

                    if (dataString != null) {
                      results = new Uri[]{Uri.parse(dataString)};
                    } else if (Build.VERSION.SDK_INT >= 26 && mCameraPhotoPath != null) {
                      results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                }
            }

            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        }
    }

    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        this.onActivityResult(requestCode, resultCode, data);
    }

    public void onNewIntent(Intent intent) {}

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
          imageFileName,  /* prefix */
          ".jpg",         /* suffix */
          storageDir      /* directory */
        );

        return imageFile;
    }
}
