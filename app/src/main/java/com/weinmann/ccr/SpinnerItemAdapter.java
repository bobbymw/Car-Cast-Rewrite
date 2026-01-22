package com.weinmann.ccr;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.List;
import java.util.Objects;

public class SpinnerItemAdapter<T> extends ArrayAdapter<SpinnerItem<T>> {

    public SpinnerItemAdapter(Context context, List<SpinnerItem<T>> podcasts) {
        super(context, android.R.layout.simple_spinner_item, podcasts);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    public int getIndexByValue(T value) {
        for (int i = 0; i < this.getCount(); ++i) {
            if (Objects.requireNonNull(getItem(i)).value().equals(value)) {
                return i;
            }
        }

        return -1;
    }
}

