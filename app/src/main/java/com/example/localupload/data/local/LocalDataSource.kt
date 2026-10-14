package com.example.localupload.data.local

import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadStatus
import java.util.concurrent.CopyOnWriteArrayList

class LocalDataSource {

    companion object {

        /*
         * Shared across ViewModel + Worker.
         */
        private val history =
            CopyOnWriteArrayList<UploadHistory>()
    }

    fun getAll(): List<UploadHistory> {
        return history.sortedByDescending {
            it.createdAt
        }
    }

    fun getPending(): List<UploadHistory> {
        return history
            .filter {
                it.status == UploadStatus.PENDING ||
                        it.status == UploadStatus.FAILED
            }
            .sortedByDescending {
                it.createdAt
            }
    }

    fun insert(item: UploadHistory) {
        history.removeAll {
            it.id == item.id
        }

        history.add(item)
    }

    fun update(item: UploadHistory) {
        history.removeAll {
            it.id == item.id
        }

        history.add(item)
    }

    fun delete(id: String) {
        history.removeAll {
            it.id == id
        }
    }

    fun clearAll() {
        history.clear()
    }
}