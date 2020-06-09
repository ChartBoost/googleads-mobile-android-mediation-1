// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.ads.mediation.chartboost;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.chartboost.sdk.Banner.BannerSize;
import com.chartboost.sdk.Chartboost.CBFramework;
import com.chartboost.sdk.ChartboostBanner;
import com.chartboost.sdk.ChartboostBannerListener;
import com.chartboost.sdk.Events.ChartboostCacheError;
import com.chartboost.sdk.Events.ChartboostCacheEvent;
import com.chartboost.sdk.Events.ChartboostClickError;
import com.chartboost.sdk.Events.ChartboostClickEvent;
import com.chartboost.sdk.Events.ChartboostShowError;
import com.chartboost.sdk.Events.ChartboostShowEvent;
import com.chartboost.sdk.Model.CBError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.MediationBannerAdapter;
import com.google.android.gms.ads.mediation.MediationBannerListener;
import com.google.android.gms.ads.mediation.MediationInterstitialAdapter;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.ads.mediation.chartboost.ChartboostAdapterUtils.chartboostAdSizeFromLocalExtras;

/**
 * The {@link ChartboostAdapter} class is used to load Chartboost rewarded-based video &
 * interstitial ads and mediate the callbacks between Chartboost SDK and Google Mobile Ads SDK.
 */
@Keep
public class ChartboostAdapter extends ChartboostMediationAdapter
    implements MediationInterstitialAdapter, MediationBannerAdapter, ChartboostBannerErrorListener {

  protected static final String TAG = ChartboostAdapter.class.getSimpleName();

  /**
   * Custom chartboost error code. Banner cannot be created due to invalid supported size
   */
  protected static final int ERROR_BANNER_SIZE_MISMATCH = 101;

  /**
   * Flag to keep track of whether or not this {@link ChartboostAdapter} is loading ads.
   */
  private boolean mIsLoading;

  /**
   * Mediation interstitial listener used to forward interstitial ad events from Chartboost SDK to
   * Google Mobile Ads SDK.
   */
  private MediationInterstitialListener mMediationInterstitialListener;

  /**
   * Mediation banner listener used to forward banner ad events from Chartboost SDK to Google Mobile
   * Ads SDK.
   */
  private MediationBannerListener mMediationBannerListener;

  /**
   * A Chartboost extras object used to store optional information used when loading ads.
   */
  private ChartboostParams mChartboostParams = new ChartboostParams();

  /**
   * The Abstract Chartboost adapter delegate used to forward events received from {@link
   * ChartboostSingleton} to Google Mobile Ads SDK for interstitial ads.
   */

  /**
   * Banner object {@link ChartboostBanner}
   */
  private ChartboostBanner mChartboostBanner;

  /**
   * FrameLayout use as a {@link ChartboostBanner} container.
   */
  private FrameLayout mBannerContainer;

  /**
   * Context reference {@link Context}
   */
  private Context mContext;

  /**
   * Boolean which keeps track if banner is loaded to avoid forwarding more than once.
   */
  private AtomicBoolean isAdLoaded = new AtomicBoolean(false);

  private AbstractChartboostAdapterDelegate mChartboostInterstitialDelegate =
      new AbstractChartboostAdapterDelegate() {

        @Override
        public ChartboostParams getChartboostParams() {
          return mChartboostParams;
        }

        @Override
        public void didInitialize() {
          super.didInitialize();

          // Request ChartboostSingleton to load interstitial ads once the Chartboost
          // SDK is initialized.
          mIsLoading = true;
          ChartboostSingleton.loadInterstitialAd(mChartboostInterstitialDelegate);
        }

        @Override
        public void didCacheInterstitial(String location) {
          super.didCacheInterstitial(location);

          if (mMediationInterstitialListener != null && mIsLoading
              && location.equals(mChartboostParams.getLocation())) {
            mIsLoading = false;
            mMediationInterstitialListener.onAdLoaded(ChartboostAdapter.this);
          }
        }

        @Override
        public void didFailToLoadInterstitial(String location,
            CBError.CBImpressionError error) {
          super.didFailToLoadInterstitial(location, error);
          if (mMediationInterstitialListener != null
              && location.equals(mChartboostParams.getLocation())) {
            if (mIsLoading) {
              mIsLoading = false;
              mMediationInterstitialListener.onAdFailedToLoad(ChartboostAdapter.this,
                  ChartboostAdapterUtils.getAdRequestErrorType(error));
            } else if (error
                == CBError.CBImpressionError.INTERNET_UNAVAILABLE_AT_SHOW) {
              // Chartboost sends the CBErrorInternetUnavailableAtShow error when
              // the Chartboost SDK fails to show an ad because no network connection
              // is available.
              mMediationInterstitialListener.onAdOpened(ChartboostAdapter.this);
              mMediationInterstitialListener.onAdClosed(ChartboostAdapter.this);
            }
          }
        }

        @Override
        public void didDismissInterstitial(String location) {
          super.didDismissInterstitial(location);
          if (mMediationInterstitialListener != null) {
            mMediationInterstitialListener.onAdClosed(ChartboostAdapter.this);
          }
        }

        @Override
        public void didClickInterstitial(String location) {
          super.didClickInterstitial(location);
          if (mMediationInterstitialListener != null) {
            // Chartboost doesn't have a delegate method for when an ad left
            // application. Assuming that when an interstitial ad is clicked and the
            // user is taken out of the application to show a web page, we forward
            // the ad left application event to Google Mobile Ads SDK.
            mMediationInterstitialListener.onAdClicked(ChartboostAdapter.this);
            mMediationInterstitialListener.onAdLeftApplication(ChartboostAdapter.this);
          }
        }

        @Override
        public void didDisplayInterstitial(String location) {
          super.didDisplayInterstitial(location);
          if (mMediationInterstitialListener != null) {
            mMediationInterstitialListener.onAdOpened(ChartboostAdapter.this);
          }
        }
      };

  @Override
  public void requestInterstitialAd(Context context,
      MediationInterstitialListener mediationInterstitialListener,
      Bundle serverParameters,
      MediationAdRequest mediationAdRequest,
      Bundle networkExtras) {
    mMediationInterstitialListener = mediationInterstitialListener;

    mChartboostParams = ChartboostAdapterUtils
        .createChartboostParams(serverParameters, networkExtras);
    if (!ChartboostAdapterUtils.isValidChartboostParams(mChartboostParams)) {
      // Invalid server parameters, send ad failed to load event.
      if (mMediationInterstitialListener != null) {
        mMediationInterstitialListener.onAdFailedToLoad(ChartboostAdapter.this,
            AdRequest.ERROR_CODE_INVALID_REQUEST);
      }
      return;
    }

    if (!ChartboostAdapterUtils.isValidContext(context)) {
      // Chartboost initialization failed, send initialization failed event.
      String logMessage = "Failed to request ad from Chartboost: Internal Error.";
      Log.w(TAG, logMessage);
      mMediationInterstitialListener.onAdFailedToLoad(ChartboostAdapter.this,
          AdRequest.ERROR_CODE_INVALID_REQUEST);
      return;
    }

    ChartboostSingleton.startChartboostInterstitial(context, mChartboostInterstitialDelegate);
  }

  @Override
  public void showInterstitial() {
    // Request ChartboostSingleton to show interstitial ads.
    ChartboostSingleton.showInterstitialAd(mChartboostInterstitialDelegate);
  }

  @Override
  public void onDestroy() {
    if (mChartboostBanner != null) {
      removeBannerDelegate();
      mChartboostBanner.detachBanner();
      mChartboostBanner = null;
    }
  }

  @Override
  public void onPause() {
  }

  @Override
  public void onResume() {
  }

  @Override
  public void requestBannerAd(Context context,
      MediationBannerListener mediationBannerListener,
      Bundle serverParameters,
      AdSize adSize,
      MediationAdRequest mediationAdRequest,
      Bundle networkExtras) {
    mContext = context;
    mMediationBannerListener = mediationBannerListener;
    mChartboostParams = ChartboostAdapterUtils.createChartboostParams(
        serverParameters,
        networkExtras);
    if (!ChartboostAdapterUtils.isValidChartboostParams(mChartboostParams)) {
      // Invalid server parameters, send ad failed to load event.
      if (mMediationBannerListener != null) {
        mMediationBannerListener.onAdFailedToLoad(
            ChartboostAdapter.this,
            AdRequest.ERROR_CODE_INVALID_REQUEST);
      }
      return;
    }

    BannerSize supportedAdSize = chartboostAdSizeFromLocalExtras(context, adSize);
    if (supportedAdSize == null) {
      mMediationBannerListener.onAdFailedToLoad(ChartboostAdapter.this,
          ERROR_BANNER_SIZE_MISMATCH);
      return;
    }

    mChartboostParams.setBannerSize(supportedAdSize);
    ChartboostSingleton.startChartboostBanner(
        context,
        mChartboostBannerDelegate,
        this);
  }

  @Override
  public View getBannerView() {
    return mBannerContainer;
  }

  /**
   * Create new {@link ChartboostBanner} object and wrap banner in the container view.
   *
   * @param context
   * @param params
   * @param bannerListener
   */
  @NonNull
  private ChartboostBanner initBanner(Context context,
      ChartboostParams params,
      ChartboostBannerListener bannerListener) {
    String location = params.getLocation();
    // Attach object to layout to inflate the banner.
    mBannerContainer = new FrameLayout(context);
    FrameLayout.LayoutParams paramsLayout = new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT);
    paramsLayout.gravity = Gravity.CENTER_HORIZONTAL;

    mChartboostBanner = new ChartboostBanner(context,
        location,
        params.getBannerSize(),
        bannerListener);
    mChartboostBanner.setAutomaticallyRefreshesContent(false);
    mBannerContainer.addView(mChartboostBanner, paramsLayout);
    return mChartboostBanner;
  }

  /**
   * In case of error or when banner is destroyed, remove its delegate to allow creating new banner
   */
  private void removeBannerDelegate() {
    if (mChartboostBanner != null) {
      ChartboostSingleton.removeBannerDelegate(mChartboostBanner.getLocation());
    } else {
      ChartboostSingleton.removeBannerDelegate(mChartboostParams.getLocation());
    }
  }

  private ChartboostBannerListener mChartboostBannerListener = new ChartboostBannerListener() {
    @Override
    public void onAdCached(ChartboostCacheEvent chartboostCacheEvent,
        ChartboostCacheError chartboostCacheError) {
      if (mMediationBannerListener != null && mChartboostBanner != null) {
        if (chartboostCacheError == null) {
          if(!isAdLoaded.getAndSet(true)) {
            mMediationBannerListener.onAdLoaded(
                ChartboostAdapter.this);
            mChartboostBanner.show();
          }
        } else {
          mMediationBannerListener.onAdFailedToLoad(
              ChartboostAdapter.this,
              chartboostCacheError.code);
          removeBannerDelegate();
        }
      }
    }

    @Override
    public void onAdShown(ChartboostShowEvent chartboostShowEvent,
        ChartboostShowError chartboostShowError) {
      if (mMediationBannerListener != null) {
        if (chartboostShowError == null) {
          mMediationBannerListener.onAdOpened(
              ChartboostAdapter.this);
        } else {
          Log.w(TAG, "Chartboost show error: "+chartboostShowError.code);
        }
      }
    }

    @Override
    public void onAdClicked(ChartboostClickEvent chartboostClickEvent,
        ChartboostClickError chartboostClickError) {
      if (chartboostClickError == null && mMediationBannerListener != null) {
        mMediationBannerListener.onAdClicked(
            ChartboostAdapter.this);
        mMediationBannerListener.onAdLeftApplication(
            ChartboostAdapter.this);
      }
    }
  };

  private AbstractChartboostAdapterDelegate mChartboostBannerDelegate = new AbstractChartboostAdapterDelegate() {
    @Override
    public ChartboostParams getChartboostParams() {
      return mChartboostParams;
    }

    @Override
    public void didInitialize() {
      super.didInitialize();
      initBanner(mContext, mChartboostParams, mChartboostBannerListener).cache();
    }
  };

  @Override
  public void notifyBannerInitializationError(int error) {
    if (mMediationBannerListener != null) {
      mMediationBannerListener.onAdFailedToLoad(this, error);
      removeBannerDelegate();
    }
  }

  /**
   * The {@link com.google.ads.mediation.chartboost.ChartboostAdapter
   * .ChartboostExtrasBundleBuilder} class is used to create a networkExtras bundle which can be
   * passed to the adapter to make network specific customizations.
   */
  public static final class ChartboostExtrasBundleBuilder {

    /**
     * Key to add and obtain {@link #cbFramework}.
     */
    static final String KEY_FRAMEWORK = "framework";

    /**
     * Key to add and obtain {@link #cbFrameworkVersion}.
     */
    static final String KEY_FRAMEWORK_VERSION = "framework_version";

    /**
     * Framework being used to load Charboost ads.
     */
    private CBFramework cbFramework;

    /**
     * The version name of {@link #cbFramework}.
     */
    private String cbFrameworkVersion;

    public ChartboostExtrasBundleBuilder setFramework(CBFramework framework, String version) {
      this.cbFramework = framework;
      this.cbFrameworkVersion = version;
      return this;
    }

    public Bundle build() {
      Bundle bundle = new Bundle();
      bundle.putSerializable(KEY_FRAMEWORK, cbFramework);
      bundle.putString(KEY_FRAMEWORK_VERSION, cbFrameworkVersion);
      return bundle;
    }
  }
}
