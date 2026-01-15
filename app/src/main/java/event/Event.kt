package event

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Event(
    @DocumentId val id: String? = null,
    val name: String? = null,
    val owner: String? = null,
    var imageUrl: String? = null,
    val creation: Timestamp? = null,
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val tags: List<String>? = null,
)
