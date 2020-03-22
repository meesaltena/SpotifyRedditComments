package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Track
import kotlin.random.Random


class MainActivity : AppCompatActivity() {

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var notification_ids: ArrayList<Int> = arrayListOf()
    private var last_played: Track? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        val CLIENT_ID = getString(R.string.SPOTIFY_API_KEY)
        val REDIRECT_URI = getString(R.string.SPOTIFY_CALLBACK_URL)

        // Create NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel("SpotifyRedditComments", name, importance)
            mChannel.description = descriptionText

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }

        // Set the spotify connection parameters
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected to Spotify remote!")
                // Now you can start interacting with App Remote
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
                // Something went wrong when attempting to connect! Handle errors here
            }
        })

    }

    private fun connected() {

        // Subscribe to the SpotifyAppRemote PlayerState event, triggers when the playerstate is updated
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback {

            // Get the currently playing track
            val track: Track = it.track
            if (last_played == null || !last_played?.equals(track)!!) {

                // Log the playing track
                Log.d(
                    "MainActivity",
                    "Playing: " + track.name + " by " + track.artist.name + " with id: " + track.uri
                )

                // Update the textview to display the currently playing track
                UpdateTextView(track)

                var reddit_search_url =
                    "https://api.reddit.com/search.json?syntax=cloudsearch&q=url:" + track.uri.replace(
                        "spotify:track:",
                        "")

                val queue = Volley.newRequestQueue(this)

                Log.d("MainActivity", "Reddit search url: " + reddit_search_url)
                val textView2 = findViewById(R.id.textView2) as TextView

                // Request a string response from the provided URL.
                val stringRequest = StringRequest(
                    Request.Method.GET, reddit_search_url,
                    Response.Listener<String> { response ->

                        var jsonResponse: RedditResponse = Gson().fromJson(response, RedditResponse::class.java);

                        // If the count of children is >0, we found at least 1 reddit thread
                        if (jsonResponse.data.asJsonObject.get("children").asJsonArray.count() > 0) {

                            // Get permalink of the thread with the most upvotes
                            val permalink = GetMostUpvotedThreadPermalink(jsonResponse.data.asJsonObject.get("children").asJsonArray)
                            Log.d("MainActivity", "Permalink: " + permalink)


                            textView2.text = getString(R.string.permalink, permalink)
                            createAndShowNotification(permalink, track)
                        }
                    },
                    Response.ErrorListener { textView2.text = "That didn't work!" })

                // Add the request to the RequestQueue.
                last_played = track;
                queue.add(stringRequest)
            }
        }
    }

    // Returns permalink of the thread with the highest number of upvotes.
    private fun GetMostUpvotedThreadPermalink(array: JsonArray?): String {

        // Sets thread to the first object (reddit thread) in the list
        var MostUpvotedThread: JsonObject? = array?.get(0)?.asJsonObject;

        // For each thread:
        // If the no. of upvotes in the current thread is higher than that of the current MostUpvotedThread,
        // Set MostUpvotedThread to the current thread
        array?.forEach { jsonElement: JsonElement? ->
            val ups = jsonElement?.asJsonObject?.get("data")?.asJsonObject?.get("ups")?.asString?.toInt();
            if(ups!! > MostUpvotedThread?.asJsonObject?.get("data")?.asJsonObject?.get("ups")?.asString?.toInt()!!){
                MostUpvotedThread = jsonElement?.asJsonObject;
            }
        }

        // Return the permalink of the most upvoted thread
        return "https://reddit.com" + MostUpvotedThread?.asJsonObject?.get("data")?.asJsonObject?.get("permalink")?.asString
    }

    private fun UpdateTextView(track: Track) {
        val textView = findViewById(R.id.textView) as TextView
        textView.text = getString(
            R.string.playing_track,
            track.name,
            track.artist.name,
            track.uri.replace("spotify:track:", "")
        )
    }

    private fun createAndShowNotification(permalink: String, track: Track) {

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(permalink))
        val pendingIntent: PendingIntent = PendingIntent.getActivities(
            this, 0,
            arrayOf(intent), PendingIntent.FLAG_UPDATE_CURRENT
        )

        var builder = NotificationCompat.Builder(this, "SpotifyRedditComments")
            .setContentTitle("Thread found!")
            .setSmallIcon(R.drawable.reddit_icon)
            .setContentText(track.artist.name + " - " + track.name)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Generating a random notification id
        var notification_id = 0;

        do {
            notification_id = Random.nextInt(0, 1000000);
        } while (notification_ids.contains(notification_id))

        notification_ids.add(notification_id)

        // Show the notification
        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(notification_id, builder.build())
        }
    }


    override fun onStop() {
        super.onStop()
        // Aaand we will finish off here.
    }
}
