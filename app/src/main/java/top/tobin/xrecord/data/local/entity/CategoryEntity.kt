package top.tobin.xrecord.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收支分类，支持二级。
 *
 * `parentId` 为 null 表示一级分类；系统预置分类只能隐藏不能删除，避免用户误删后
 * 历史流水变成孤儿数据。
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["parentId"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: Long,
    val parentId: Long? = null,
    val name: String,
    val icon: String,
    val color: String,
    val type: CategoryType,
    val sortOrder: Int = 0,
    val isPreset: Boolean = false,
    val isHidden: Boolean = false,
)
