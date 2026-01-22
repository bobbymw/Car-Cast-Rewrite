package com.weinmann.ccr;

import android.content.Context;
import android.view.*;
import android.widget.*;

import com.weinmann.ccr.itunes.ITunesSearchResponse.ITunesPodcastResult;

import java.util.List;

public class PodcastSearchAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private List<ITunesPodcastResult> items;

    public PodcastSearchAdapter(Context ctx, List<ITunesPodcastResult> items) {
        this.inflater = LayoutInflater.from(ctx);
        this.items = items;
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int i) { return items.get(i); }

    @Override
    public long getItemId(int i) { return i; }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        View v = convertView;

        if (v == null) {
            v = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
        }

        TextView title = v.findViewById(android.R.id.text1);
        TextView url   = v.findViewById(android.R.id.text2);

        ITunesPodcastResult item = items.get(i);
        title.setText(item.title);
        url.setText(item.podcastUrl);

        return v;
    }

    public void update(List<ITunesPodcastResult> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }
}
