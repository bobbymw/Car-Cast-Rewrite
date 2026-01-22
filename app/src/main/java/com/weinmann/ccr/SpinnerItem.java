package com.weinmann.ccr;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public record SpinnerItem<T>(String label, T value) {
    @Contract(pure = true)
    @NonNull
    @Override
    public String toString() {
        return label();
    }
}
