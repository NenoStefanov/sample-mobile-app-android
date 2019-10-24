package com.miracl.mpinsdk.mobileapploginsample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.miracl.mpinsdk.MPinMFA;
import com.miracl.mpinsdk.MPinMfaAsync;
import com.miracl.mpinsdk.model.Status;
import com.miracl.mpinsdk.model.User;
import com.miracl.mpinsdk.model.VerificationResult;

public class DeepLinkRegistrationActivity extends AppCompatActivity implements EnterPinDialog.EventListener {

    private String mActivationToken;
    private String mUserId;
    private String mVerificationCode;
    private User mCurrentUser;
    private EnterPinDialog mEnterPinDialog;
    private MessageDialog mMessageDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deep_link_registration);
        mEnterPinDialog = new EnterPinDialog(DeepLinkRegistrationActivity.this, DeepLinkRegistrationActivity.this);
        mMessageDialog = new MessageDialog(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onStop() {
        super.onStop();
        mEnterPinDialog.dismiss();
        // In order not to clutter the sample app with users, remove the current user if it is not registered on
        // navigating away from the app
//        if (mCurrentUser != null && mCurrentUser.getState() != User.State.REGISTERED) {
//            SampleApplication.getMfaSdk().deleteUser(mCurrentUser, null);
//        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    public void onPinEntered(String pin) {
        if (mCurrentUser == null) {
            return;
        }

        // Once we have the user's pin we can finish the registration process
        SampleApplication.getMfaSdk().finishRegistration(mCurrentUser, new String[]{pin}, new MPinMfaAsync.Callback<Void>() {

            @Override
            protected void onSuccess(@Nullable Void result) {
                // The registration for the user is complete
                startActivity(new Intent(DeepLinkRegistrationActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            protected void onFail(final @NonNull Status status) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        // Finishing registration has failed
                        mMessageDialog.show(status);
                    }
                });
            }
        });
    }

    @Override
    public void onPinCanceled() {

    }

    private void handleIntent(final Intent intent) {
        if (intent != null) {
            boolean launchedFromHistory = (intent
                    .getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;

            if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW) && intent
                    .getDataString() != null && !launchedFromHistory) {

                Uri verificationUri = Uri.parse(intent.getDataString());
                mUserId = verificationUri.getQueryParameter("user_id");
                mVerificationCode = verificationUri.getQueryParameter("code");

                if (mUserId != null && mVerificationCode != null) {
                    configureSdkAndStartVerification();
                } else {
                    Toast.makeText(this, R.string.message_invalid_verification_uri, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void configureSdkAndStartVerification() {
        SampleApplication.getMfaSdk().doInBackground(new MPinMfaAsync.Callback<MPinMFA>() {

            @Override
            protected void onResult(@NonNull Status status, @Nullable MPinMFA sdk) {
                if (sdk != null) {
                    // Set the cid and the backend with which the SDK will be configured
                    sdk.setCid(getString(R.string.mpin_cid));
                    Status setBackendStatus = sdk.setBackend(getString(R.string.mpin_backend));
                    if (setBackendStatus.getStatusCode() == com.miracl.mpinsdk.model.Status.Code.OK) {
                        finishVerification();
                    } else {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(DeepLinkRegistrationActivity.this,
                                        "The MPin SDK did not initialize properly. Check your backend and CID configuration",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        });
    }

    private void finishVerification() {
        User user = SampleApplication.getMfaSdk().getMfaSdk().makeNewUser(mUserId);
        SampleApplication.getMfaSdk().finishVerification(user, mVerificationCode, new MPinMfaAsync.Callback<VerificationResult>() {
            @Override
            protected void onSuccess(@Nullable VerificationResult result) {
                super.onSuccess(result);
                mActivationToken = result.activationToken;
                SampleApplication.setCurrentAccessCode(result.accessCode);
                startRegistration();
            }

            @Override
            protected void onFail(@NonNull final Status status) {
                super.onFail(status);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMessageDialog.show(status);
                    }
                });
            }
        });
    }

    private void startRegistration() {
        SampleApplication.getMfaSdk().startRegistration(SampleApplication.getCurrentAccessCode(),
                mUserId,
                "",
                null,
                mActivationToken,
                new MPinMfaAsync.Callback<User>() {
                    @Override
                    protected void onSuccess(@Nullable User result) {
                        super.onSuccess(result);
                        mCurrentUser = result;
                        confirmIdentity();
                    }

                    @Override
                    protected void onFail(@NonNull final Status status) {
                        super.onFail(status);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMessageDialog.show(status);
                            }
                        });
                    }
                });
    }

    private void confirmIdentity() {
        SampleApplication.getMfaSdk().confirmRegistration(mCurrentUser, new MPinMfaAsync.Callback<Void>() {
            @Override
            protected void onSuccess(@Nullable Void result) {
                super.onSuccess(result);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mEnterPinDialog.show();
                    }
                });
            }

            @Override
            protected void onFail(@NonNull final Status status) {
                super.onFail(status);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMessageDialog.show(status);
                    }
                });
            }
        });
    }
}
