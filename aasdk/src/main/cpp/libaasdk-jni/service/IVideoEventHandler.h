#pragma once

namespace service
{

    class IVideoEventHandler
    {
    public:
        typedef IVideoEventHandler* Pointer;

        virtual ~IVideoEventHandler() = default;
        virtual void onAVChannelStartIndication() = 0;
        virtual void onAVChannelStopIndication() = 0;
    };

}
