package tech.glasgowneuro.attysecg;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Locale;

import tech.glasgowneuro.attyscomm.AttysComm;


/**
 * Created by Bernd Porr on 20/01/17.
 * Heartrate Plot
 */

public class HeartratePlotFragment extends Fragment {

    String TAG = "HeartratePlotFragment";

    private static final float MAXBPM = 200;
    private static final int HISTORY_SIZE = 60;
    private static final int HRVSCALING = 5;

    private SimpleXYSeries bpmHistorySeries = null;

    private SimpleXYSeries nrmssdHistorySeries = null;

    private XYPlot bpmPlot = null;

    private TextView bpmText = null;
    private TextView nrmssdText = null;

    private double avgHR = 0;
    private double devHR = 0;
    private double rmsHR = 0;
    private double nrmssd = 0;

    private Button bpmResetButton = null;

    private ToggleButton bpmAutoscaleButton = null;

    private TextView bpmStatsView = null;

    View view = null;


    private class DataRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        private final String FILENAME = "hr.tsv";

        private PrintWriter textdataFileStream = null;

        // starts the recording
        private DataRecorder() {
            try {
                File f = new File(AttysECG.ATTYSDIR, FILENAME);
                textdataFileStream = new PrintWriter(new FileOutputStream(f, true));
            } catch (java.io.FileNotFoundException e) {
                textdataFileStream = null;
            }
        }

        // are we recording?
        public boolean isRecording() {
            return (textdataFileStream != null);
        }

        private void saveData(float bpm) {
            if (textdataFileStream == null) return;
            char s = 9;
            long t = System.currentTimeMillis();
            String tmp = String.format(Locale.US, "%d%c", t, s);
            tmp = tmp + String.format(Locale.US, "%f", bpm);
            if (textdataFileStream != null) {
                textdataFileStream.format("%s\n", tmp);
                textdataFileStream.flush();
            }
        }
    }

    private DataRecorder dataRecorder = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        Log.d(TAG, "onCreate, creating Fragment");

        if (container == null) {
            return null;
        }

        view = inflater.inflate(R.layout.heartrateplotfragment, container, false);

        // setup the APR Levels plot:
        bpmPlot = view.findViewById(R.id.bpmPlotView);
        bpmText = view.findViewById(R.id.bpmTextView);
        nrmssdText = view.findViewById(R.id.nrmssdTextView);

        bpmHistorySeries = new SimpleXYSeries("BPM");
        bpmHistorySeries.useImplicitXVals();

        nrmssdHistorySeries = new SimpleXYSeries(String.format(Locale.US, "HRV (x%d)", HRVSCALING));
        nrmssdHistorySeries.useImplicitXVals();

        bpmPlot.setRangeBoundaries(0, MAXBPM, BoundaryMode.FIXED);
        bpmPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        bpmPlot.addSeries(bpmHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        bpmPlot.addSeries(nrmssdHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(0, 255, 0), null, null, null));
        bpmPlot.setDomainLabel("Heartbeat #");
        bpmPlot.setRangeLabel("");

        if (getActivity() != null) {
            Screensize screensize = new Screensize(getActivity().getWindowManager());

            if (screensize.isTablet()) {
                bpmPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 25);
            } else {
                bpmPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 50);
            }
        }

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        bpmPlot.getGraph().setDomainGridLinePaint(paint);
        bpmPlot.getGraph().setRangeGridLinePaint(paint);

        bpmStatsView = view.findViewById(R.id.bpmstats);
        bpmResetButton = view.findViewById(R.id.bpmreset);
        bpmAutoscaleButton = view.findViewById(R.id.bpmautoscale);

        bpmAutoscaleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getActivity() == null) return;
                Screensize screensize = new Screensize(getActivity().getWindowManager());
                if (isChecked) {
                    if (screensize.isTablet()) {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 5);
                    } else {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 20);
                    }
                    bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
                } else {
                    if (screensize.isTablet()) {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 25);
                    } else {
                        bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 50);
                    }
                    bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
                }
                bpmPlot.redraw();
            }
        });
        bpmAutoscaleButton.setChecked(true);

        bpmResetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reset();
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (prefs.getBoolean("hrv_logging", true)) {
            AttysECG.createSubDir();
            dataRecorder = new DataRecorder();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (prefs.getBoolean("hrv_logging", true)) {
            if (null == dataRecorder) {
                dataRecorder = new DataRecorder();
            }
        }
    }


    private void reset() {
        int n = bpmHistorySeries.size();
        for (int i = 0; i < n; i++) {
            bpmHistorySeries.removeLast();
        }
        n = nrmssdHistorySeries.size();
        for (int i = 0; i < n; i++) {
            nrmssdHistorySeries.removeLast();
        }
        bpmPlot.redraw();
    }

    private double hr2interval(int i) {
        return 60.0 / bpmHistorySeries.getY(i).doubleValue();
    }

    public synchronized void addValue(final float v) {

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (bpmText != null) {
                        bpmText.setText(String.format(Locale.US, "%03d BPM", (int) v));
                    }
                    if (nrmssdText != null) {
                        nrmssdText.setText(String.format(Locale.US, "%1.0f%% HRV", nrmssd * 100));
                    }
                }
            });
        }

        if (bpmHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "bpmHistorySeries == null");
            }
            return;
        }
        if (nrmssdHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "nrmssdHistorySeries == null");
            }
            return;
        }

        // get rid the oldest sample in history:
        if (bpmHistorySeries.size() > HISTORY_SIZE) {
            bpmHistorySeries.removeFirst();
        }

        // get rid the oldest sample in history:
        if (nrmssdHistorySeries.size() > HISTORY_SIZE) {
            nrmssdHistorySeries.removeFirst();
        }

        double sum = 0;
        final int startIdx = bpmHistorySeries.size() / 2;
        if (bpmStatsView != null) {
            int n = 0;
            for (int i = startIdx; i < bpmHistorySeries.size(); i++) {
                sum = sum + hr2interval(i);
                n++;
            }
            avgHR = sum / n;
            double dev = 0;
            for (int i = startIdx; i < bpmHistorySeries.size(); i++) {
                dev = dev + Math.pow(hr2interval(i) - avgHR, 2);
            }
            devHR = Math.sqrt(dev / (n - 1));

            n = 0;
            sum = 0;
            for (int i = startIdx; i < bpmHistorySeries.size() - 1; i++) {
                sum = sum + Math.pow(hr2interval(i) - hr2interval(i + 1), 2);
                n++;
            }
            rmsHR = Math.sqrt(sum / n);
            if (avgHR > 0) {
                Double a = rmsHR / avgHR;
                if (!a.isInfinite() && !a.isNaN()) {
                    nrmssd = a;
                }
            }
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bpmStatsView.setText(String.format(Locale.US,
                            "avg = %3.02f BPM, sd = %3.02f ms, rmssd = %3.02f ms",
                            60 / avgHR, devHR * 1000, rmsHR * 1000));
                }
            });
        }

        // add the latest history sample:
        bpmHistorySeries.addLast(null, v);

        float maxBpm = MAXBPM;

        if (bpmAutoscaleButton.isChecked()) {
            maxBpm = 0;
            for (int i = 0; i < bpmHistorySeries.size(); i++) {
                float b = bpmHistorySeries.getY(i).floatValue();
                if (b > maxBpm) maxBpm = b;
            }
            bpmPlot.setRangeBoundaries(0, maxBpm, BoundaryMode.FIXED);
        }

        double nr = nrmssd;
        if (nr > (100 / HRVSCALING)) nr = 100.0 / HRVSCALING;
        nrmssdHistorySeries.addLast(null, nr * maxBpm * HRVSCALING);
        bpmPlot.redraw();

        if (null != dataRecorder) {
            dataRecorder.saveData(v);
        }

    }
}
