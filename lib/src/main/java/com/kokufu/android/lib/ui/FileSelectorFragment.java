/*
 * Copyright (C) 2015 Yusuke Miura
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kokufu.android.lib.ui;

import com.kokufu.android.lib.ui.fileselectorfragment.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * A fragment representing a list of Files.
 * <p>
 * Activities containing this fragment or TargetFragment of this MUST implement
 * the {@link com.kokufu.android.lib.ui.FileSelectorFragment.OnFileSelectorFragmentInteractionListener}
 * interface.
 * </p>
 */
public class FileSelectorFragment extends Fragment {
    public enum SelectionType {
        FILE,
        DIR
    }

    private static final String ARG_DIR = "dir";
    private static final String ARG_TYPE = "type";
    private static final String ARG_BACK_KEY_INTERRUPTION = "backKeyInterruption";

    private TextView mPathView;
    private ListView mListView;
    private ProgressBar mProgress;
    private File mDir;
    private SelectionType mType = SelectionType.FILE;
    private boolean mBackKeyInterruption = true;
    private FileLoadTask mFileLoadTask;
    private OnFileSelectorFragmentInteractionListener mListener;

    /**
     * Use this factory method to create a new instance of this from Activity.
     * If you want to create this from Fragment,
     * use {@link #newInstance(android.support.v4.app.Fragment, com.kokufu.android.lib.ui.FileSelectorFragment.SelectionType, java.io.File, boolean)}
     *
     * @param type selection type
     * @param dir the dir which is shown first.
     * @return A new instance of fragment FileSelectorFragment.
     */
    public static FileSelectorFragment newInstance(SelectionType type, File dir, boolean backKeyInterruption) {
        return newInstance(null, type, dir, backKeyInterruption);
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment
     *
     * @param type selection type
     * @param dir the dir which is shown first.
     * @return A new instance of fragment FileSelectorFragment.
     */
    public static FileSelectorFragment newInstance(Fragment targetFragment, SelectionType type, File dir, boolean backKeyInterruption) {
        FileSelectorFragment fragment = new FileSelectorFragment();
        if (targetFragment != null) {
            fragment.setTargetFragment(targetFragment, -1);
        }
        Bundle args = new Bundle();
        args.putSerializable(ARG_TYPE, type);
        args.putSerializable(ARG_DIR, dir);
        args.putBoolean(ARG_BACK_KEY_INTERRUPTION, backKeyInterruption);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Required empty public constructor.
     * Do not call this method manually.
     */
    public FileSelectorFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Type & Back key interruption
        if (getArguments() != null) {
            mType = (SelectionType) getArguments().getSerializable(ARG_TYPE);
            mBackKeyInterruption = getArguments().getBoolean(ARG_BACK_KEY_INTERRUPTION);
        }

        // Dir
        File dir = null;
        if (savedInstanceState != null) {
            dir = (File) savedInstanceState.getSerializable(ARG_DIR);
        } else if (getArguments() != null) {
            dir = (File) getArguments().getSerializable(ARG_DIR);
        }
        if (dir == null) {
            dir = Environment.getExternalStorageDirectory();
            if (dir == null || !dir.exists()) {
                dir = Environment.getRootDirectory();
            }
        }
        setDir(dir);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(ARG_DIR, mDir);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        boolean isListenerImplemented = false;
        Fragment targetFragment = getTargetFragment();
        if (targetFragment != null) {
            try {
                mListener = (OnFileSelectorFragmentInteractionListener) targetFragment;
                isListenerImplemented = true;
            } catch (ClassCastException e) {
                // Do nothing
            }
        }
        if (!isListenerImplemented) {
            try {
                mListener = (OnFileSelectorFragmentInteractionListener) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException("target fragment must be set or " + activity
                        + " must implement OnFileListFragmentInteractionListener");
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_file_selector, container, false);

        mPathView = (TextView) v.findViewById(R.id.textView1);
        mListView = (ListView) v.findViewById(R.id.listView);
        mProgress = (ProgressBar) v.findViewById(R.id.progressBar);

        mPathView.setHorizontallyScrolling(true);
        mPathView.setMovementMethod(ScrollingMovementMethod.getInstance());

        mListView.setOnItemClickListener(mOnItemClickListener);

        // Back key interruption
        v.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                return mBackKeyInterruption && event.getAction() == KeyEvent.ACTION_UP
                        && keyCode == KeyEvent.KEYCODE_BACK && moveToParentDir();
            }
        });
        // This is required to enable setOnKeyListener
        v.setFocusableInTouchMode(true);

        return v;
    }

    public File getDir() {
        return mDir;
    }

    public void setDir(File dir) {
        if (mFileLoadTask != null) {
            mFileLoadTask.cancel(true);
        }
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(getActivity(),
                    getString(R.string.error_dir_access),
                    Toast.LENGTH_SHORT).show();
        } else {
            mFileLoadTask = new FileLoadTask(dir);
            mFileLoadTask.execute();
        }
    }

    /**
     *
     * @return {@code false} when there is no parent.
     */
    public boolean moveToParentDir() {
        if (mDir == null || mDir.getParentFile() == null) {
            return false;
        }
        setDir(mDir.getParentFile());
        return true;
    }

    private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            File f = (File) parent.getItemAtPosition(position);

            if (mType == SelectionType.FILE && f.isDirectory()) {
                setDir(f);
            } else {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the00j9p;l/ku7iop@@[]p:o;il
                    // fragment is attached to one) that an item has been selected.
                    mListener.onFileSelected(FileSelectorFragment.this, f);
                }
            }
        }
    };

    private class FileLoadTask extends AsyncTask<Void, Void, List<File>> {
        private final File mLoadingDir;

        /**
         * @param dir to be loaded. it must not be {@code null}
         */
        public FileLoadTask(File dir) {
            mLoadingDir = dir;
        }

        @Override
        protected void onPreExecute() {
            if (mProgress != null) {
                mProgress.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected List<File> doInBackground(Void... params) {
            if (mLoadingDir == null || mLoadingDir.listFiles() == null) {
                return null;
            }

            List<File> dirs = new ArrayList<>();
            List<File> files = new ArrayList<>();
            for (File f : mLoadingDir.listFiles()) {
                if (f.isDirectory()) {
                    dirs.add(f);
                } else {
                    files.add(f);
                }
            }
            Collections.sort(dirs);
            Collections.sort(files);
            if (mType == SelectionType.FILE) {
                dirs.addAll(files);
            }
            return dirs;
        }

        @Override
        protected void onPostExecute(List<File> result) {
            if (isCancelled()) {
                return;
            }
            Context context = getActivity();
            if (context == null) {
                // Already detached
                return;
            }
            if (mProgress == null || mPathView == null || mListView == null) {
                return;
            }
            mProgress.setVisibility(View.GONE);
            mDir = mLoadingDir;
            mPathView.setText(mDir.getPath());
            if (result == null) {
                Toast.makeText(context,
                        getString(R.string.error_dir_access),
                        Toast.LENGTH_SHORT).show();
                mListView.setAdapter(null);
            } else if (result.size() == 0) {
                // setEmptyText(getString(R.string.empty_dir));
                mListView.setAdapter(null);
            } else {
                mListView.setAdapter(new FileListAdapter(context, result));
            }
            mFileLoadTask = null;
        }
    }

    private static class FileListAdapter extends BaseAdapter {
        private DateFormat mDateFormat;
        private DateFormat mTimeFormat;
        private final Context mContext;
        private final List<File> mFiles;

        public FileListAdapter(Context context, List<File> files) {
            mContext = context;
            mFiles = files;
            mDateFormat = android.text.format.DateFormat.getDateFormat(context);
            mTimeFormat = android.text.format.DateFormat.getTimeFormat(context);
        }

        @Override
        public int getCount() {
            return mFiles.size();
        }

        @Override
        public Object getItem(int position) {
            return mFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(mContext);
                convertView = inflater.inflate(R.layout.list_item_file, parent, false);
            }

            ImageView iconView = (ImageView) convertView.findViewById(R.id.imageView1);
            TextView tv1 = (TextView) convertView.findViewById(R.id.textView1);
            TextView tv2 = (TextView) convertView.findViewById(R.id.textView2);

            int textColor = tv1.getTextColors().getDefaultColor();

            File file = (File) getItem(position);
            if (isColorDark(textColor)) {
                if (file.isDirectory()) {
                    iconView.setImageResource(R.drawable.ic_dir_black);
                } else {
                    iconView.setImageResource(R.drawable.ic_doc_black);
                }
            } else {
                if (file.isDirectory()) {
                    iconView.setImageResource(R.drawable.ic_dir_white);
                } else {
                    iconView.setImageResource(R.drawable.ic_doc_white);
                }
            }

            tv1.setText(file.getName());

            Date lastModifyDate = new Date(file.lastModified());
            tv2.setText(mDateFormat.format(lastModifyDate) +
                    " " +
                    mTimeFormat.format(lastModifyDate));

            return convertView;
        }
    }

    private static boolean isColorDark(int color){
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated to
     * the activity and potentially other fragments contained in that activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFileSelectorFragmentInteractionListener {
        public void onFileSelected(FileSelectorFragment parent, File file);
    }
}
