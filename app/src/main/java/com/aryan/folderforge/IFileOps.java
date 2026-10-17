package com.aryan.folderforge;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IFileOps extends IInterface {
    String applyZip(String zipPath, String destinationPath) throws RemoteException;
    String removeGameAssetBundles(String destinationPath) throws RemoteException;
    String installRemovePack(String zipPath, String destinationPath) throws RemoteException;

    abstract class Stub extends Binder implements IFileOps {
        private static final String DESCRIPTOR = "com.aryan.folderforge.IFileOps";
        static final int TRANSACTION_applyZip = IBinder.FIRST_CALL_TRANSACTION;
        static final int TRANSACTION_removeGameAssetBundles = IBinder.FIRST_CALL_TRANSACTION + 1;
        static final int TRANSACTION_installRemovePack = IBinder.FIRST_CALL_TRANSACTION + 2;
        static final int TRANSACTION_destroy = 16777115;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public void destroy() { }


        public static IFileOps asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IFileOps) return (IFileOps) local;
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_destroy) {
                destroy();
                return true;
            }
            if (code == TRANSACTION_applyZip) {
                data.enforceInterface(DESCRIPTOR);
                String zip = data.readString();
                String destination = data.readString();
                String result = applyZip(zip, destination);
                reply.writeNoException();
                reply.writeString(result);
                return true;
            }
            if (code == TRANSACTION_removeGameAssetBundles) {
                data.enforceInterface(DESCRIPTOR);
                String destination = data.readString();
                String result = removeGameAssetBundles(destination);
                reply.writeNoException();
                reply.writeString(result);
                return true;
            }
            if (code == TRANSACTION_installRemovePack) {
                data.enforceInterface(DESCRIPTOR);
                String zip = data.readString();
                String destination = data.readString();
                String result = installRemovePack(zip, destination);
                reply.writeNoException();
                reply.writeString(result);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static final class Proxy implements IFileOps {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public String applyZip(String zipPath, String destinationPath)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(zipPath);
                    data.writeString(destinationPath);
                    remote.transact(TRANSACTION_applyZip, data, reply, 0);
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public String removeGameAssetBundles(String destinationPath)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(destinationPath);
                    remote.transact(TRANSACTION_removeGameAssetBundles, data, reply, 0);
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }            @Override
            public String installRemovePack(String zipPath, String destinationPath)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(zipPath);
                    data.writeString(destinationPath);
                    remote.transact(TRANSACTION_installRemovePack, data, reply, 0);
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

        }
    }
}
