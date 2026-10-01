package io.vanta.app.core;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import io.vanta.app.R;
import io.vanta.app.math.Mathf;

public class DownloadProgressDialog {
    private final Activity activity;
    private Dialog dialog;

    public DownloadProgressDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        if (dialog != null) return;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.download_progress_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    public void show() {
        show(0, null);
    }

    public void show(int textResId) {
        show(textResId, null);
    }

    public void show(Runnable onCancelCallback) {
        show(0, null, onCancelCallback);
    }

    public void show(String title, Runnable onCancelCallback) {
        show(0, title, onCancelCallback);
    }

    public void show(int textResId, final Runnable onCancelCallback) {
        show(textResId, null, onCancelCallback);
    }

    private void show(int textResId, String title, final Runnable onCancelCallback) {
        if (isShowing()) return;
        close();
        if (dialog == null) create();

        TextView titleView = dialog.findViewById(R.id.TextView);
        if (title != null) titleView.setText(title);
        else titleView.setText(textResId > 0 ? textResId : R.string.downloading_file);
        dialog.findViewById(R.id.TVStatus).setVisibility(textResId == R.string.installing_system_files ? View.VISIBLE : View.GONE);

        setProgress(-1);
        dialog.findViewById(R.id.LLBottomBar).setVisibility(onCancelCallback != null ? View.VISIBLE : View.GONE);
        if (onCancelCallback != null) {
            dialog.findViewById(R.id.BTCancel).setOnClickListener((v) -> onCancelCallback.run());
        }
        dialog.show();
    }

    public void setProgress(int progress) {
        if (dialog == null) return;
        ProgressBar progressBar = dialog.findViewById(R.id.ProgressBar);
        TextView progressText = dialog.findViewById(R.id.TVProgress);
        if (progress < 0) {
            progressBar.setIndeterminate(true);
            progressText.setVisibility(View.GONE);
        }
        else {
            progress = Mathf.clamp(progress, 0, 100);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(progress);
            progressText.setText(progress + "%");
            progressText.setVisibility(View.VISIBLE);
        }
    }

    public void close() {
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
        catch (Exception e) {}
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
