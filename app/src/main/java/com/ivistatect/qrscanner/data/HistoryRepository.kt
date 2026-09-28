package com.ivistatect.qrscanner.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** App-owned boundary over the history store. New data layer; no reference code reused. */
@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {

    fun scanned(): Flow<List<HistoryEntity>> = dao.observeByOrigin(HistoryEntity.ORIGIN_SCANNED)
    fun created(): Flow<List<HistoryEntity>> = dao.observeByOrigin(HistoryEntity.ORIGIN_CREATED)
    fun favorites(): Flow<List<HistoryEntity>> = dao.observeFavorites()

    suspend fun add(item: HistoryEntity): Long = dao.insert(item)
    suspend fun getById(id: Long): HistoryEntity? = dao.getById(id)
    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
}
