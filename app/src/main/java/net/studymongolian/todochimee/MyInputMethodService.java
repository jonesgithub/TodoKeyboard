package net.studymongolian.todochimee;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.inputmethodservice.InputMethodService;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import net.studymongolian.mongollibrary.ImeContainer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MyInputMethodService extends InputMethodService
        implements ImeContainer.OnSystemImeListener, ImeContainer.DataSource {

    ImeContainer imeContainer;

    @SuppressLint("InflateParams") // there is apparently no root view to pass in as the parent?
    @Override
    public View onCreateInputView() {
        LayoutInflater inflater = getLayoutInflater();
        imeContainer = (ImeContainer) inflater.inflate(R.layout.jianpan_yangshi, null, false);
        imeContainer.showSystemKeyboardsOption(getString(R.string.show_system_keyboards_option)); // 长按键盘键可以切换到别的系统输入法
        imeContainer.setOnSystemImeListener(this);
        imeContainer.setDataSource(this);
        return imeContainer;
    }

    // ImeContainer.OnSystemImeListener的方法

    @Override
    public InputConnection getInputConnection() {
        return getCurrentInputConnection();
    }

    @Override
    public void onChooseNewSystemKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im == null) return;
        im.showInputMethodPicker();
    }

    // ImeContainer.DataSource的方法

    @Override
    public void onRequestWordsStartingWith(String text) {
        new GetWordsStartingWith(this).execute(text);
    }

    @Override
    public void onWordFinished(String word, String previousWord) {
        new AddOrUpdateDictionaryWordsTask(this).execute(word, previousWord);
    }

    @Override
    public void onCandidateClick(int position, String word, String previousWordInEditor) {
        addSpace();
        new RespondToCandidateClick(this).execute(word, previousWordInEditor);
    }

    private void addSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(" ", 1);
    }

    @Override
    public void onCandidateLongClick(int position, String text, String previousWordInEditor) {
        new DeleteWord(this, position).execute(text, previousWordInEditor);
    }

    private static class GetWordsStartingWith extends AsyncTask<String, Integer, List<String>> {

        private WeakReference<MyInputMethodService> serviceReference;

        GetWordsStartingWith(MyInputMethodService context) {
            serviceReference = new WeakReference<>(context);
        }

        @Override
        protected List<String> doInBackground(String... params) {
            String prefix = params[0];

            Context context = serviceReference.get();

            List<String> words = new ArrayList<>();
            Cursor cursor = UserDictionary.Words.queryPrefix(context, prefix);
            if (cursor == null) return words;
            int indexWord = cursor.getColumnIndex(UserDictionary.Words.WORD);
            while (cursor.moveToNext()) {
                words.add(cursor.getString(indexWord));
            }
            cursor.close();
            return words;

        }

        @Override
        protected void onPostExecute(List<String> result) {
            MyInputMethodService service = serviceReference.get();
            if (service == null) return;

            if (result.size() > 0)
                service.imeContainer.setCandidates(result);
            else
                service.imeContainer.clearCandidates();
        }
    }

    private static class AddOrUpdateDictionaryWordsTask extends AsyncTask<String, Integer, Void> {

        private WeakReference<MyInputMethodService> serviceReference;

        AddOrUpdateDictionaryWordsTask(MyInputMethodService context) {
            serviceReference = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(String... params) {
            String word = params[0];
            String previousWord = params[1];
            Context context = serviceReference.get();
            insertUpdateWord(context, word);

            UserDictionary.Words.addFollowing(context, previousWord, word);
            return null;
        }

    }

    private static void insertUpdateWord(Context context, String word) {
        if (context == null) return;

        int id = UserDictionary.Words.incrementFrequency(context, word);
        if (id < 0) {
            UserDictionary.Words.addWord(context, word);
        }

    }


    private static class RespondToCandidateClick extends AsyncTask<String, Integer, List<String>> {

        private WeakReference<MyInputMethodService> serviceReference;

        RespondToCandidateClick(MyInputMethodService context) {
            serviceReference = new WeakReference<>(context);
        }

        @Override
        protected List<String> doInBackground(String... params) {
            String word = params[0];
            String previousWord = params[1];
            MyInputMethodService service = serviceReference.get();


            UserDictionary.Words.incrementFrequency(service, word);
            UserDictionary.Words.addFollowing(service, previousWord, word);
            return UserDictionary.Words.getFollowing(service, word);
        }

        @Override
        protected void onPostExecute(List<String> followingWords) {
            MyInputMethodService service = serviceReference.get();
            if (service == null) return;
            if (followingWords.size() == 0) {
                service.imeContainer.clearCandidates();
            } else {
                service.imeContainer.setCandidates(followingWords);
            }
        }
    }

    private static class DeleteWord extends AsyncTask<String, Integer, Void> {

        private WeakReference<MyInputMethodService> serviceReference;
        private int index;

        DeleteWord(MyInputMethodService context, int index) {
            serviceReference = new WeakReference<>(context);
            this.index = index;
        }

        @Override
        protected Void doInBackground(String... params) {
            String word = params[0];
            String previousWord = params[1];
            Context context = serviceReference.get();
            UserDictionary.Words.deleteWord(context, word);
            UserDictionary.Words.deleteFollowingWord(context, previousWord, word);
            return null;
        }

        @Override
        protected void onPostExecute(Void results) {
            MyInputMethodService service = serviceReference.get();
            if (service == null) return;
            service.imeContainer.removeCandidate(index);
        }
    }
}