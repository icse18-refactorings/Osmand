package net.osmand.plus.osmo;

import java.util.ArrayList;
import java.util.List;

import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.IconsCache;
import net.osmand.plus.NavigationService;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dashboard.DashLocationFragment;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by Denis
 * on 20.01.2015.
 */
public class DashOsMoFragment extends DashLocationFragment implements OsMoGroups.OsMoGroupsUIListener {

	public static final String TAG = "DASH_OSMO_FRAGMENT";

	private Handler uiHandler = new Handler();

	OsMoPlugin plugin;

	@Override
	public void onCloseDash() {
		if (plugin != null && plugin.getGroups() != null) {
			plugin.getGroups().removeUiListener(this);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		plugin = OsmandPlugin.getEnabledPlugin(OsMoPlugin.class);

		View view = getActivity().getLayoutInflater().inflate(R.layout.dash_osmo_fragment, container, false);
		view.findViewById(R.id.manage).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				launchOsMoGroupsActivity();
			}
		});

		setupHader(view);
		return view;
	}

	@Override
	public void onOpenDash() {
		plugin = OsmandPlugin.getEnabledPlugin(OsMoPlugin.class);
		if (plugin != null) {
			plugin.getGroups().addUiListeners(this);
		}
		setupOsMoView();
	}

	private void setupOsMoView() {
		View mainView = getView();

		if (plugin == null) {
			mainView.setVisibility(View.GONE);
			return;
		} else {
			mainView.setVisibility(View.VISIBLE);
		}

		updateStatus();
	}

	private void setupHader(final View header) {
		CompoundButton enableService = (CompoundButton)header.findViewById(R.id.header_layout).findViewById(R.id.check_item);
		CompoundButton trackr = (CompoundButton) header.findViewById(R.id.card_content).findViewById(R.id.check_item);

		enableService.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				plugin.getService().connect(true);
			}
		});

		final OsmandApplication app = getMyApplication();
		trackr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					if (plugin != null && plugin.getTracker() != null) {
						plugin.getTracker().enableTracker();
					}
					app.startNavigationService(NavigationService.USED_BY_LIVE);
					//interval setting not needed here, handled centrally in app.startNavigationService
					//app.getSettings().SERVICE_OFF_INTERVAL.set(0);
				} else {
					if (plugin != null && plugin.getTracker() != null) {
						plugin.getTracker().disableTracker();
					}
					if (app.getNavigationService() != null) {
						app.getNavigationService().stopIfNeeded(app, NavigationService.USED_BY_LIVE);
					}
				}
				updateStatus();
			}
		});
		ImageButton share = (ImageButton) header.findViewById(R.id.share);
		IconsCache cache = getMyApplication().getIconsCache();
		share.setImageDrawable(cache.getContentIcon(R.drawable.ic_action_gshare_dark));
		share.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				OsMoGroupsActivity.shareSessionUrl(plugin, getActivity());
			}
		});
		updateStatus();
	}

	private void updateStatus() {

		View header = getView();
		if (getView() == null) {
			return;
		}

		View cardContent = header.findViewById(R.id.card_content);
		View enableOsmo = header.findViewById(R.id.header_layout).findViewById(R.id.check_item);
		if (plugin.getService().isEnabled()) {
			cardContent.setVisibility(View.VISIBLE);
			enableOsmo.setVisibility(View.GONE);
		} else {
			cardContent.setVisibility(View.GONE);
			enableOsmo.setVisibility(View.VISIBLE);
			getClearContentList(header);
			return;
		}

		CompoundButton trackr = (CompoundButton) header.findViewById(R.id.check_item);
		if (plugin != null && plugin.getTracker() != null) {
			trackr.setChecked(plugin.getTracker().isEnabledTracker());
		}

		updateConnectedDevices(header);
	}

	private void updateConnectedDevices(View mainView) {
		OsMoGroups grps = plugin.getGroups();
		OsMoGroupsStorage.OsMoGroup mainGroup = null;
		ArrayList<OsMoGroupsStorage.OsMoGroup> groups = new ArrayList<>(grps.getGroups());
		for (OsMoGroupsStorage.OsMoGroup grp : groups) {
			if (grp.getGroupId() == null) {
				mainGroup = grp;
				groups.remove(grp);
				break;
			}
		}
		LinearLayout contentList = getClearContentList(mainView);
		if (mainGroup == null) {
			return;
		}

		String trackerId = plugin.getService().getMyGroupTrackerId();
		List<OsMoGroupsStorage.OsMoDevice> devices =
				new ArrayList<>(mainGroup.getVisibleGroupUsers(trackerId));

		if (devices.size() > 3) {
			while (devices.size() > 3) {
				devices.remove(devices.size() - 1);
			}
		} else {
			if (groups.size() > 0){
				for (OsMoGroupsStorage.OsMoGroup grp : groups) {
					if (grp.getVisibleGroupUsers(trackerId).size() > 0) {
						
					}
				}
			}
		}


		setupDeviceViews(contentList, devices);
//		if (devices.size() < 3 && groups.size() > 0) {
//			setupGroupsViews(3 - devices.size(), groups, contentList);
//		}
	}

	private LinearLayout getClearContentList(View mainView) {
		LinearLayout contentList = (LinearLayout) mainView.findViewById(R.id.items);
		contentList.removeAllViews();
		return contentList;
	}

	private void setupGroupsViews(int toAddCount, ArrayList<OsMoGroupsStorage.OsMoGroup> groups, LinearLayout contentList) {
		int counter = 1;
		LayoutInflater inflater = getActivity().getLayoutInflater();
		for (final OsMoGroupsStorage.OsMoGroup group : groups) {
			View v = inflater.inflate(R.layout.dash_osmo_item, null, false);
			v.findViewById(R.id.direction_icon).setVisibility(View.GONE);
			v.findViewById(R.id.distance).setVisibility(View.GONE);
			v.findViewById(R.id.show_on_map).setVisibility(View.GONE);
			v.findViewById(R.id.check_item).setVisibility(View.GONE);
			final String name = group.getVisibleName(getActivity());
			//TODO show group users
			TextView peopleCount = (TextView) v.findViewById(R.id.people_count);
			peopleCount.setText(String.valueOf(group.getGroupUsers(null).size()));
			ImageView icon = (ImageView) v.findViewById(R.id.icon);
			icon.setImageDrawable(getMyApplication().getIconsCache().getContentIcon(R.drawable.ic_group));
			((TextView) v.findViewById(R.id.name)).setText(name);
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					launchOsMoGroupsActivity();
				}
			});
			contentList.addView(v);
			if (counter == toAddCount) {
				return;
			}
			counter++;
		}
	}

	private void setupDeviceViews(LinearLayout contentList, List<OsMoGroupsStorage.OsMoDevice> devices) {
		Drawable markerIcon = getMyApplication().getIconsCache().getContentIcon(R.drawable.ic_action_marker_dark);
		LayoutInflater inflater = getActivity().getLayoutInflater();
		List<DashLocationFragment.DashLocationView> distances = new ArrayList<DashLocationFragment.DashLocationView>();
		for (final OsMoGroupsStorage.OsMoDevice device : devices) {
			View v = inflater.inflate(R.layout.dash_osmo_item, null, false);
			v.findViewById(R.id.people_icon).setVisibility(View.GONE);
			v.findViewById(R.id.people_count).setVisibility(View.GONE);
			final ImageButton showOnMap = (ImageButton) v.findViewById(R.id.show_on_map);
			showOnMap.setImageDrawable(markerIcon);
			final String name = device.getVisibleName();
			final Location loc = device.getLastLocation();
			showOnMap.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					if (loc == null) {
						Toast.makeText(getActivity(), R.string.osmo_device_not_found, Toast.LENGTH_SHORT).show();
						return;
					}
					getMyApplication().getSettings().setMapLocationToShow(loc.getLatitude(),
							loc.getLongitude(), 15,
							new PointDescription(PointDescription.POINT_TYPE_MARKER, name),
							false, device); //$NON-NLS-1$
					MapActivity.launchMapActivityMoveToTop(getActivity());
				}
			});

			ImageView direction = (ImageView) v.findViewById(R.id.direction_icon);
			direction.setVisibility(View.VISIBLE);
			TextView label = (TextView) v.findViewById(R.id.distance);
			DashLocationFragment.DashLocationView dv = new DashLocationFragment.DashLocationView(direction, label, loc != null ? new LatLon(loc.getLatitude(),
					loc.getLongitude()) : null);
			distances.add(dv);

			final CompoundButton enableDevice = (CompoundButton) v.findViewById(R.id.check_item);
			ImageView icon = (ImageView) v.findViewById(R.id.icon);
			if (device.isEnabled()) {
				enableDevice.setVisibility(View.GONE);
				icon.setImageDrawable(getMyApplication().getIconsCache().
						getPaintedContentIcon(R.drawable.ic_person, device.getColor()));
			} else {
				enableDevice.setVisibility(View.VISIBLE);
				enableDevice.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						plugin.getGroups().connectDevice(device);
						refreshItems();
					}
				});
				showOnMap.setVisibility(View.GONE);
				icon.setImageDrawable(getMyApplication().getIconsCache().
						getContentIcon(R.drawable.ic_person));
			}

			if (device.isActive()) {

			}
			((TextView) v.findViewById(R.id.name)).setText(name);
			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					launchOsMoGroupsActivity();
				}
			});
			contentList.addView(v);
		}
		this.distances = distances;
	}

	private void refreshItems() {
		if (!uiHandler.hasMessages(OsMoGroupsActivity.LIST_REFRESH_MSG_ID)) {
			Message msg = Message.obtain(uiHandler, new Runnable() {
				@Override
				public void run() {
					updateConnectedDevices(getView());
				}
			});
			msg.what = OsMoGroupsActivity.LIST_REFRESH_MSG_ID;
			uiHandler.sendMessageDelayed(msg, 100);
		}
	}

	private void launchOsMoGroupsActivity() {
		Intent intent = new Intent(getActivity(), OsMoGroupsActivity.class);
		getActivity().startActivity(intent);
	}

	@Override
	public void groupsListChange(String operation, OsMoGroupsStorage.OsMoGroup group) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateStatus();
			}
		});
	}

	@Override
	public void deviceLocationChanged(OsMoGroupsStorage.OsMoDevice device) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateConnectedDevices(getView());
				updateAllWidgets();
			}
		});
	}
}
