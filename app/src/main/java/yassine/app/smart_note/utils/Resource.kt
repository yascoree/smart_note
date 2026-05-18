package yassine.app.smart_note.utils

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {
    class Idle<T> : Resource<T>()
    class Loading<T> : Resource<T>()
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
}