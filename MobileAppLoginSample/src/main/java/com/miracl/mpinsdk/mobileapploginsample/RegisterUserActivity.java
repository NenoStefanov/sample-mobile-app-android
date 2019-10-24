package com.miracl.mpinsdk.mobileapploginsample;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.miracl.mpinsdk.MPinMfaAsync;
import com.miracl.mpinsdk.model.Status;
import com.miracl.mpinsdk.model.User;

import static android.view.View.GONE;

public class RegisterUserActivity extends AppCompatActivity implements View.OnClickListener {

    private EditText mEmailInput;
    private Button mSubmitButton;
    private View mConfirmControls;
    private Button mResendButton;
    private MessageDialog mMessageDialog;

    private User mCurrentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_user);
        mMessageDialog = new MessageDialog(this);
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetViews();
    }

    private void onResendClick() {
        if (mCurrentUser == null) {
            return;
        }

        disableControls();
        // If for some reason we need to resend the verification mail, the verification process for the user must be
        // restarted
        SampleApplication.getMfaSdk().startVerification(mCurrentUser,
                getString(R.string.mpin_client_id),
                getString(R.string.mpin_redirect_uri),
                SampleApplication.getCurrentAccessCode(),
                new MPinMfaAsync.Callback<Void>() {

                    @Override
                    protected void onSuccess(@Nullable Void result) {
                        // If restarting the registration process is successful a new verification mail is sent
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                enableControls();
                                mMessageDialog.show("Email has been sent to " + mCurrentUser.getId());
                            }
                        });
                    }

                    @Override
                    protected void onFail(final @NonNull Status status) {
                        // Restarting registration has failed
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                mMessageDialog.show(status);
                                enableControls();
                            }
                        });
                    }
                });
    }

    private void onSubmitClick() {
        final String email = mEmailInput.getText().toString().trim();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mMessageDialog.show("Invalid email address entered");
            return;
        }

        disableControls();
        new AccessCodeObtainingTask(getString(R.string.access_code_service_base_url), new AccessCodeObtainingTask.Callback() {

            @Override
            public void onSuccess() {
                onStartedRegistration(email);
            }

            @Override
            public void onFail(Status status) {
                mMessageDialog.show(status);
                enableControls();
            }
        }).execute();
    }

    private void onStartedRegistration(final String email) {
        // Obtain a user object from the SDK. The id of the user is an email and while it is not mandatory to provide
        // device name note that some backends may require it
        SampleApplication.getMfaSdk().makeNewUser(email, "Android Sample App", new MPinMfaAsync.Callback<User>() {

            @Override
            protected void onSuccess(@Nullable User result) {
                mCurrentUser = result;
                // After we have a user, we can start the verification process for it. If successful this will trigger sending a
                // confirmation email from the current backend
                SampleApplication.getMfaSdk()
                        .startVerification(mCurrentUser,
                                getString(R.string.mpin_client_id),
                                getString(R.string.mpin_redirect_uri),
                                SampleApplication.getCurrentAccessCode(),
                                new MPinMfaAsync.Callback<Void>() {

                                    @Override
                                    protected void onSuccess(@Nullable Void result) {
                                        // When the verification process is started successfully for a user, the identity is
                                        // stored in the SDK, associating it with the current set backend.
                                        runOnUiThread(new Runnable() {

                                            @Override
                                            public void run() {
                                                mSubmitButton.setVisibility(GONE);
                                                mConfirmControls.setVisibility(View.VISIBLE);
                                                mMessageDialog.show("Email has been sent to " + mCurrentUser.getId());
                                                enableControls();
                                            }
                                        });
                                    }

                                    @Override
                                    protected void onFail(final @NonNull Status status) {
                                        // Starting verification has failed
                                        runOnUiThread(new Runnable() {

                                            @Override
                                            public void run() {
                                                mMessageDialog.show(status);
                                                enableControls();
                                            }
                                        });
                                    }
                                });
            }

            @Override
            protected void onFail(final @NonNull Status status) {
                // Starting verification has failed
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        mMessageDialog.show(status);
                        enableControls();
                    }
                });
            }
        });
    }

    private void onEmailChanged(CharSequence textInput) {
        if (mSubmitButton.getVisibility() != View.VISIBLE) {
            disableControls();

            // If the email is changed after the verification is started, we delete the identity (because it will get
            // stored otherwise) and effectively restart the verification process. This is solely not to clutter the
            // sample app with users
            SampleApplication.getMfaSdk().deleteUser(mCurrentUser, new MPinMfaAsync.Callback<Void>() {

                @Override
                protected void onResult(@NonNull Status status, @Nullable Void result) {
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            mCurrentUser = null;
                            mConfirmControls.setVisibility(View.GONE);
                            mSubmitButton.setVisibility(View.VISIBLE);
                            enableControls();
                        }
                    });
                }
            });
        } else {
            if (textInput.length() == 0 && mSubmitButton.isEnabled()) {
                mSubmitButton.setEnabled(false);
            } else {
                mSubmitButton.setEnabled(true);
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.register_submit_button:
                onSubmitClick();
                return;
            case R.id.register_resend_button:
                onResendClick();
                return;
            default:
                break;
        }
    }

    private void disableControls() {
        mSubmitButton.setEnabled(false);
        mResendButton.setEnabled(false);
        mEmailInput.setEnabled(false);
    }

    private void enableControls() {
        mSubmitButton.setEnabled(true);
        mResendButton.setEnabled(true);
        mEmailInput.setEnabled(true);
    }

    private void initViews() {
        mEmailInput = findViewById(R.id.register_email_input);
        mConfirmControls = findViewById(R.id.register_confirm_controls);

        mSubmitButton = findViewById(R.id.register_submit_button);
        mSubmitButton.setOnClickListener(this);
        mResendButton = findViewById(R.id.register_resend_button);
        mResendButton.setOnClickListener(this);

        mEmailInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                onEmailChanged(charSequence);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void resetViews() {
        mEmailInput.setText("");
        mSubmitButton.setEnabled(false);
        mSubmitButton.setVisibility(View.VISIBLE);
        mConfirmControls.setVisibility(GONE);
    }
}
