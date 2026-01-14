package eventSearch

import android.content.Intent
import android.os.Bundle
import android.view.OrientationEventListener
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.piastcity.MyLocationDemoActivity
import com.example.piastcity.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import event.Event
import eventCreation.EventCreator
import event.Event as PartyEvent

class EventSearchActivity : AppCompatActivity(), EventSearchRecyclerAdapter.OnItemListener {
    private val database = Firebase.firestore
    private lateinit var addEventButton: FloatingActionButton
    private lateinit var refreshButton: FloatingActionButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var eventList: ArrayList<PartyEvent>
    private val email = FirebaseAuth.getInstance().currentUser!!.email
    private lateinit var orientationEventListener: OrientationEventListener

    override fun onResume() {
        super.onResume()
        setSearchView()
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                if (orientation in 45..134 || orientation in 225..314) {
                    layoutManager.orientation = LinearLayoutManager.HORIZONTAL
                } else {
                    layoutManager.orientation = LinearLayoutManager.VERTICAL
                }
            }
        }

        orientationEventListener?.enable()
    }

    override fun onPause() {
        super.onPause()

        orientationEventListener?.disable()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_search)

        // attach RecyclerView
        recyclerView = findViewById(R.id.eventRecyclerView)

        setRecycler()
        setButtons()
    }

    private fun setRecycler() {
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.orientation = LinearLayoutManager.VERTICAL
        linearLayoutManager.reverseLayout = true
        linearLayoutManager.stackFromEnd = true
        recyclerView.layoutManager = linearLayoutManager

        recyclerView.isNestedScrollingEnabled = false
    }

    private fun setButtons() {
        addEventButton = findViewById(R.id.addEventButton)
        refreshButton = findViewById(R.id.refreshButton)
        addEventButton.setOnClickListener {
            val goToEventCreator = Intent(this, MyLocationDemoActivity::class.java)
            goToEventCreator.putExtra("isCreator", true)
            startActivity(goToEventCreator)
            setSearchView()
        }
        refreshButton.setOnClickListener{
            setSearchView()
        }
    }

    private fun setSearchView() {
        eventList = ArrayList()
        database.collection("events").orderBy("creation").get().addOnSuccessListener {
            if(!it.isEmpty) {
                for (data in it.documents) {
                    val event = data.toObject<PartyEvent>()
                    eventList.add(event!!)
                }
            }
            recyclerView.adapter = EventSearchRecyclerAdapter(eventList, this)
        }
    }

    override fun onItemClick(position: Int, event: Event) {
        val mapsIntent = Intent(this, MyLocationDemoActivity::class.java)
        mapsIntent.putExtra("isCreator", false)
        mapsIntent.putExtra("localization_longitude", event.longitude)
        mapsIntent.putExtra("localization_latitude", event.latitude)
        startActivityForResult(mapsIntent, 69)
    }

    override fun onItemLongClick(position: Int, event: Event) {
        database.collection("events")
            .whereEqualTo("name", event.name)
            .whereEqualTo("owner", email)
            .whereEqualTo("creation", event.creation)
            .get().addOnSuccessListener {documents ->
                if(!documents.isEmpty) {
                    eventList.removeAt(position)
                    recyclerView.adapter!!.notifyItemRemoved(position)
                    val documentID = documents.documents[0].id
                    database.collection("events")
                        .document(documentID)
                        .delete()
                        .addOnSuccessListener {
                            removeImageFromStorage(event)
                        }
                }
            }
    }

    private fun removeImageFromStorage(event: Event) {
        val storageRef = FirebaseStorage.getInstance().reference
        event.imageUrl?.let { storageRef.storage.getReferenceFromUrl(it).delete() }
        //return result
    }
}