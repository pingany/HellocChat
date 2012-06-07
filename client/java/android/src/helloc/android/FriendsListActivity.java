package helloc.android;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import helloc.protocol.Message;
import helloc.core.*;

class FriendsListAdapter extends ArrayAdapter<Friend>
{
	FriendsListAdapter(Context context, List<Friend> list)
	{
		super(context, android.R.layout.simple_list_item_1, list);
	}
}

public class FriendsListActivity extends GenericActivity
		implements
			HellocClient.FriendsChangedListener
{
	/** Called when the activity is first created. */

	ListView friendslistView;

	FriendsListAdapter friendsListAdapater;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		friendsListAdapater = new FriendsListAdapter(this, client.getFriends());

		setContentView(R.layout.main);
		friendslistView = (ListView) this.findViewById(R.id.friends_list);
		friendslistView.setOnItemClickListener(new FriendItemClickListener());

		friendslistView.setAdapter(friendsListAdapater);
	}

	private class FriendItemClickListener implements OnItemClickListener
	{

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id)
		{
			assert parent == friendslistView;
			Friend f = (Friend) parent.getItemAtPosition(position);
			Intent intent = new Intent(FriendsListActivity.this,
					ChatActivity.class);
			intent.putExtra("friendId", f.getUserid());
			startActivity(intent);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		friendsListAdapater.notifyDataSetChanged();
		client.addFriendsChangedListener(this);
	}

	@Override
	public void onPause()
	{
		client.removeFriendsChangedListener(this);
		super.onPause();
	}

	public void friendChanged(List<Message.Friend> fs)
	{
		friendsListAdapater.notifyDataSetChanged();
	}

}