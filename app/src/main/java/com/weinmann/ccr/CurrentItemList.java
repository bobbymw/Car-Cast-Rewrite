package com.weinmann.ccr;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.function.Predicate;

public class CurrentItemList<T> extends ArrayList<T> {
    private int currentIndex = -1;

    public T getCurrentItem() {
        fixCurrentIndex();
        if (isEmpty()) return null;

        return get(currentIndex);
    }

    public int getCurrentIndex() {
        fixCurrentIndex();
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        currentIndex = index;
    }

    public int indexOf(Predicate<T> predicate) {
        for (int i = 0; i < size(); ++i) {
            if (predicate.test(get(i))) {
                return i;
            }
        }

        return -1;
    }

    public void replaceCurrentItem(@Nullable T newItem) {
        if (newItem == null) return;

        set(currentIndex, newItem);
    }

    private void fixCurrentIndex() {
        if (isEmpty()) {
            currentIndex = -1;
        } else if (currentIndex < 0) {
            currentIndex = 0;
        } else if (currentIndex >= size()) {
            currentIndex = size() - 1;
        }
    }
}
