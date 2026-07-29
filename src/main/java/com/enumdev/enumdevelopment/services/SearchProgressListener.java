package com.enumdev.enumdevelopment.services;

import com.enumdev.enumdevelopment.models.SearchProgressSnapshot;

public interface SearchProgressListener {

    boolean isCancelled();

    void onProgress(SearchProgressSnapshot snapshot);
}
