// DrawingPath.kt

package com.weproz.superreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class DrawingPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float = 5f,
    val penType: PenType = PenType.BRUSH,
)

// Data transfer object for saving to the database
data class PathData(
    val points: List<Pair<Float, Float>>,
    val color: Int,
    val strokeWidth: Float,
    val penType: PenType
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromPathDataList(pathData: List<PathData>?): String? {
        return gson.toJson(pathData)
    }

    @TypeConverter
    fun toPathDataList(json: String?): List<PathData>? {
        val type = object : TypeToken<List<PathData>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromPenType(penType: PenType): String = penType.name

    @TypeConverter
    fun toPenType(name: String): PenType = PenType.valueOf(name)

    @TypeConverter
    fun fromGridType(gridType: GridType): String = gridType.name

    @TypeConverter
    fun toGridType(name: String): GridType = GridType.valueOf(name)
}