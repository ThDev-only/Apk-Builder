package com.apk.builder.logger;

import android.text.style.ForegroundColorSpan;
import android.text.Spannable;
import android.text.SpannableString;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

public class Logger {
	
	private LogAdapter adapter;
	private LinearLayoutManager layoutManager;
	private List<Log> data = new ArrayList<>();
    
	private RecyclerView mRecyclerView;
	private boolean mAttached;
	
	public void attach(RecyclerView view) {
		mRecyclerView = view;
		init();
	}
	
	private void init() {
		adapter = new LogAdapter(data);
	    layoutManager = new LinearLayoutManager(mRecyclerView.getContext());
		layoutManager.setStackFromEnd(true);
		mRecyclerView.setLayoutManager(layoutManager);
		mRecyclerView.setAdapter(adapter);
	}
	
	public void d(String tag, String message) {
		mRecyclerView.post(() -> {
		    data.add(new Log("["+tag+"]", message));
	    	adapter.notifyItemInserted(data.size());
			scroll();
                
		});
	}
	
	public void e(String tagg,  String message) {
        String tag = "[" + tagg + "]";
		mRecyclerView.post(() -> {
		    Spannable messageSpan = new SpannableString(message);
		    messageSpan.setSpan(new ForegroundColorSpan(0xffff0000), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			
			Spannable tagSpan = new SpannableString(tag);
		    tagSpan.setSpan(new ForegroundColorSpan(0xffff0000), 0, tag.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);    
                
			data.add(new Log(tagSpan, messageSpan));
			adapter.notifyItemInserted(data.size());
			scroll();
		});
	}
	
	public void w(String tagg,  String message) {
        String tag = "[" + tagg + "]";
        
		mRecyclerView.post(() -> {
		    Spannable messageSpan = new SpannableString(message);
		    messageSpan.setSpan(new ForegroundColorSpan(0xffff7043), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			
            Spannable tagSpan = new SpannableString(tag);
		    tagSpan.setSpan(new ForegroundColorSpan(0xffff7043), 0, tag.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);    
                
			data.add(new Log(tagSpan, messageSpan));
			adapter.notifyItemInserted(data.size());
			scroll();
		});
	}
	
	private void scroll() {
		mRecyclerView.smoothScrollToPosition(data.size() - 1);
	}
}