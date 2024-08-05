package org.fossify.gallery.aes;


import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by michaeldunn on 3/13/17.
 */

@UnstableApi
public class EncryptedFileDataSourceFactory implements DataSource.Factory {

  private Cipher mCipher;
  private SecretKeySpec mSecretKeySpec;
  private IvParameterSpec mIvParameterSpec;
  private TransferListener mTransferListener;

  public EncryptedFileDataSourceFactory(Cipher cipher, SecretKeySpec secretKeySpec, IvParameterSpec ivParameterSpec, TransferListener listener) {
    mCipher = cipher;
    mSecretKeySpec = secretKeySpec;
    mIvParameterSpec = ivParameterSpec;
    mTransferListener = listener;
  }

  @Override
  public EncryptedFileDataSource createDataSource() {
    return new EncryptedFileDataSource(mCipher, mSecretKeySpec, mIvParameterSpec, mTransferListener);
  }

}
